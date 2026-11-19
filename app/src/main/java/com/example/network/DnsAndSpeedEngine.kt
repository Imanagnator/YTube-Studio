package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.model.DnsBenchmarkResult
import com.example.data.model.DnsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong

class DnsAndSpeedEngine(
    private val context: Context
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    companion object {
        val BUILT_IN_DNS_PROVIDERS = listOf(
            DnsProvider(
                id = "system",
                name = "System Default DNS",
                primaryIp = "Auto",
                dohUrl = "",
                description = "Uses your ISP / Wi-Fi / Private DNS resolver"
            ),
            DnsProvider(
                id = "cloudflare",
                name = "Cloudflare 1.1.1.1",
                primaryIp = "1.1.1.1",
                dohUrl = "https://cloudflare-dns.com/dns-query",
                description = "Ultra-low latency global DoH resolver"
            ),
            DnsProvider(
                id = "cloudflare_sec",
                name = "Cloudflare Malware Filter",
                primaryIp = "1.1.1.2",
                dohUrl = "https://security.cloudflare-dns.com/dns-query",
                description = "Blocks malware & phishing domains via DoH"
            ),
            DnsProvider(
                id = "google",
                name = "Google Public DNS",
                primaryIp = "8.8.8.8",
                dohUrl = "https://dns.google/dns-query",
                description = "High reliability, optimized for YouTube CDN routing"
            ),
            DnsProvider(
                id = "quad9",
                name = "Quad9 Secure",
                primaryIp = "9.9.9.9",
                dohUrl = "https://dns.quad9.net/dns-query",
                description = "Privacy-first threat-blocking DNS over HTTPS"
            ),
            DnsProvider(
                id = "adguard",
                name = "AdGuard DNS",
                primaryIp = "94.140.14.14",
                dohUrl = "https://dns.adguard-dns.com/dns-query",
                description = "Network-level ad & tracker blocking DoH",
                isAdBlocking = true
            )
        )

        // Minimal RFC 1035 wire-format A-record DNS query for "www.youtube.com"
        private val YOUTUBE_DNS_WIRE_QUERY = byteArrayOf(
            0x12, 0x34, // Transaction ID
            0x01, 0x00, // Standard query with recursion desired
            0x00, 0x01, // 1 question
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // 0 answers/authority/additional
            0x03, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
            0x07, 'y'.code.toByte(), 'o'.code.toByte(), 'u'.code.toByte(),
            't'.code.toByte(), 'u'.code.toByte(), 'b'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x00, // Root label
            0x00, 0x01, // Type A
            0x00, 0x01  // Class IN
        )
    }

    fun getNetworkSummary(): Pair<String, Int> {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return "Unknown" to 0
        val network = cm.activeNetwork ?: return "Offline" to 0
        val caps = cm.getNetworkCapabilities(network) ?: return "Offline" to 0

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular / 5G"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN Tunnel"
            else -> "Connected"
        }
        val linkMbps = (caps.linkDownstreamBandwidthKbps / 1000).coerceAtLeast(0)
        return transport to linkMbps
    }

    suspend fun benchmarkAllProviders(providers: List<DnsProvider>): Map<String, DnsBenchmarkResult> =
        coroutineScope {
            providers.map { provider ->
                async(Dispatchers.IO) {
                    provider.id to benchmarkProvider(provider)
                }
            }.awaitAll().toMap()
        }

    suspend fun benchmarkProvider(provider: DnsProvider): DnsBenchmarkResult = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Long>()
        val probeCount = 3

        for (i in 0 until probeCount) {
            val latency = if (provider.dohUrl.isNotBlank()) {
                probeDohLatency(provider.dohUrl)
            } else if (provider.primaryIp != "Auto") {
                probeTcp53Latency(provider.primaryIp)
            } else {
                probeHttpLatency("https://www.youtube.com/generate_204")
            }
            if (latency > 0) {
                samples.add(latency)
            }
        }

        if (samples.isEmpty()) {
            DnsBenchmarkResult(
                providerId = provider.id,
                latencyMs = -1L,
                jitterMs = 0L,
                reachable = false
            )
        } else {
            val avg = samples.average().roundToLong().coerceAtLeast(1L)
            val jitter = if (samples.size > 1) {
                samples.zipWithNext { a, b -> abs(a - b) }.average().roundToLong()
            } else 0L
            DnsBenchmarkResult(
                providerId = provider.id,
                latencyMs = avg,
                jitterMs = jitter,
                reachable = true
            )
        }
    }

    private fun probeDohLatency(dohUrl: String): Long {
        return runCatching {
            val body = RequestBody.create(
                "application/dns-message".toMediaType(),
                YOUTUBE_DNS_WIRE_QUERY
            )
            val req = Request.Builder()
                .url(dohUrl)
                .post(body)
                .header("Accept", "application/dns-message")
                .build()
            val startNs = System.nanoTime()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return -1L
                resp.body?.bytes()
                ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            }
        }.getOrDefault(-1L)
    }

    private fun probeTcp53Latency(ip: String): Long {
        return runCatching {
            val startNs = System.nanoTime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, 53), 2500)
            }
            ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        }.getOrDefault(-1L)
    }

    private fun probeHttpLatency(url: String): Long {
        return runCatching {
            val req = Request.Builder().url(url).head().build()
            val startNs = System.nanoTime()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.code in 200..399) {
                    ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                } else -1L
            }
        }.getOrDefault(-1L)
    }
}
