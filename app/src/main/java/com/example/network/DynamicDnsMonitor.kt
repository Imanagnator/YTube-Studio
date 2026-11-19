package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.model.DnsBenchmarkResult
import com.example.data.model.DnsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DynamicDnsStatus(
    val isMonitoring: Boolean = false,
    val isChecking: Boolean = false,
    val activeLatencyMs: Long? = null,
    val consecutiveSlowCount: Int = 0,
    val statusLine: String = "Auto-DNS ready",
    val lastSwitchReason: String? = null
)

/**
 * Dynamically monitors the active DNS-over-HTTPS server and automatically fails over
 * to the fastest healthy server on network switches (Wi-Fi <-> 5G/LTE) or sustained slowdowns.
 *
 * Uses hysteresis (2 consecutive slow/failed probes + 60 ms improvement margin + 180 s cooldown
 * when active is still reachable) so connections never flap on transient jitter.
 */
class DynamicDnsMonitor(
    private val context: Context,
    private val dnsEngine: DnsAndSpeedEngine,
    private val scope: CoroutineScope,
    private val getProviders: () -> List<DnsProvider>,
    private val getActiveDnsId: () -> String,
    private val isAutoDnsEnabled: () -> Boolean,
    private val onBenchmarksUpdated: (Map<String, DnsBenchmarkResult>) -> Unit,
    private val onSwitchDns: suspend (provider: DnsProvider, reason: String) -> Unit
) {
    companion object {
        private const val TAG = "DynamicDnsMonitor"
        private const val CHECK_INTERVAL_MS = 120_000L
        private const val SLOW_THRESHOLD_MS = 250L
        private const val MIN_IMPROVEMENT_MS = 60L
        private const val SWITCH_COOLDOWN_MS = 180_000L
        private const val CONSECUTIVE_TRIGGERS_REQUIRED = 2
    }

    private val _status = MutableStateFlow(DynamicDnsStatus())
    val status: StateFlow<DynamicDnsStatus> = _status.asStateFlow()

    private var monitorJob: Job? = null
    private var networkCallbackRegistered = false
    private var lastNetworkKey: String? = null
    private var lastSwitchAtMs: Long = 0L
    private var consecutiveSlowOrFailCount = 0

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handleNetworkChanged(network)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val key = buildNetworkKey(network, caps)
            if (key != lastNetworkKey) {
                lastNetworkKey = key
                if (isAutoDnsEnabled()) {
                    scope.launch(Dispatchers.IO) {
                        delay(1_200L) // Let new interface settle before probing
                        rebenchmarkAndSelectFastest(
                            reasonPrefix = "Network switched",
                            bypassCooldown = true
                        )
                    }
                }
            }
        }
    }

    private fun buildNetworkKey(network: Network, caps: NetworkCapabilities): String {
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "eth"
            else -> "other"
        }
        return "${network}_$transport"
    }

    private fun handleNetworkChanged(network: Network) {
        val caps = connectivityManager?.getNetworkCapabilities(network) ?: return
        val key = buildNetworkKey(network, caps)
        if (key == lastNetworkKey) return
        lastNetworkKey = key
        if (!isAutoDnsEnabled()) return
        scope.launch(Dispatchers.IO) {
            delay(1_200L)
            rebenchmarkAndSelectFastest(
                reasonPrefix = "Network changed",
                bypassCooldown = true
            )
        }
    }

    fun start() {
        if (!networkCallbackRegistered) {
            runCatching {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            }.onFailure {
                Log.w(TAG, "Unable to register default network callback", it)
            }
        }

        if (monitorJob?.isActive == true) return
        _status.update { it.copy(isMonitoring = true) }

        monitorJob = scope.launch(Dispatchers.IO) {
            // Wait for initial YouTube load to settle before running background DNS probes
            delay(8_000L)
            if (isAutoDnsEnabled()) {
                rebenchmarkAndSelectFastest(
                    reasonPrefix = "Startup auto-tune",
                    bypassCooldown = true
                )
            }

            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                if (!isAutoDnsEnabled()) {
                    consecutiveSlowOrFailCount = 0
                    _status.update {
                        it.copy(
                            isChecking = false,
                            consecutiveSlowCount = 0,
                            statusLine = "Manual DNS mode"
                        )
                    }
                    continue
                }
                checkActiveDnsHealth()
            }
        }
    }

    fun triggerImmediateOptimization(reason: String = "Auto-DNS check") {
        if (!isAutoDnsEnabled()) return
        scope.launch(Dispatchers.IO) {
            rebenchmarkAndSelectFastest(reasonPrefix = reason, bypassCooldown = true)
        }
    }

    private suspend fun checkActiveDnsHealth() {
        val providers = getProviders()
        val activeId = getActiveDnsId()
        val activeProvider = providers.find { it.id == activeId } ?: return

        _status.update { it.copy(isChecking = true, statusLine = "Checking ${activeProvider.name}...") }
        val result = dnsEngine.benchmarkProvider(activeProvider)

        val isDead = !result.reachable || result.latencyMs <= 0L
        val isSlow = !isDead && result.latencyMs >= SLOW_THRESHOLD_MS

        if (isDead || isSlow) {
            consecutiveSlowOrFailCount += 1
            val detail = if (isDead) {
                "${activeProvider.name} unreachable ($consecutiveSlowOrFailCount/$CONSECUTIVE_TRIGGERS_REQUIRED)"
            } else {
                "${activeProvider.name} slow: ${result.latencyMs} ms ($consecutiveSlowOrFailCount/$CONSECUTIVE_TRIGGERS_REQUIRED)"
            }
            _status.update {
                it.copy(
                    isChecking = false,
                    activeLatencyMs = if (isDead) null else result.latencyMs,
                    consecutiveSlowCount = consecutiveSlowOrFailCount,
                    statusLine = detail
                )
            }

            if (consecutiveSlowOrFailCount >= CONSECUTIVE_TRIGGERS_REQUIRED) {
                consecutiveSlowOrFailCount = 0
                rebenchmarkAndSelectFastest(
                    reasonPrefix = if (isDead) "${activeProvider.name} down" else "${activeProvider.name} slowed (${result.latencyMs} ms)",
                    bypassCooldown = isDead
                )
            }
        } else {
            consecutiveSlowOrFailCount = 0
            _status.update {
                it.copy(
                    isChecking = false,
                    activeLatencyMs = result.latencyMs,
                    consecutiveSlowCount = 0,
                    statusLine = "Healthy • ${result.latencyMs} ms (auto-failover > ${SLOW_THRESHOLD_MS}ms)"
                )
            }
        }
    }

    private suspend fun rebenchmarkAndSelectFastest(
        reasonPrefix: String,
        bypassCooldown: Boolean
    ) {
        val providers = getProviders()
        if (providers.isEmpty()) return

        _status.update { it.copy(isChecking = true, statusLine = "Benchmarking fastest DNS...") }
        val results = dnsEngine.benchmarkAllProviders(providers)
        onBenchmarksUpdated(results)

        val activeId = getActiveDnsId()
        val activeResult = results[activeId]
        val activeReachable = activeResult != null && activeResult.reachable && activeResult.latencyMs > 0L

        // Evaluate all reachable resolvers (including System Default DNS) so Auto-DNS always picks the fastest path
        val fastestProvider = providers
            .mapNotNull { provider ->
                val res = results[provider.id]
                if (res != null && res.reachable && res.latencyMs > 0L) provider to res else null
            }
            .minByOrNull { it.second.latencyMs }

        if (fastestProvider == null) {
            val systemProvider = providers.find { it.id == "system" }
            if (systemProvider != null && activeId != "system") {
                val reason = "$reasonPrefix → DoH blocked, fell back to System DNS"
                lastSwitchAtMs = System.currentTimeMillis()
                onSwitchDns(systemProvider, reason)
                _status.update {
                    it.copy(
                        isChecking = false,
                        activeLatencyMs = results["system"]?.latencyMs?.takeIf { ms -> ms > 0 },
                        consecutiveSlowCount = 0,
                        statusLine = "System DNS fallback active",
                        lastSwitchReason = reason
                    )
                }
            } else {
                _status.update {
                    it.copy(
                        isChecking = false,
                        statusLine = "All DoH servers unreachable — using System DNS"
                    )
                }
            }
            return
        }

        val (bestProvider, bestResult) = fastestProvider
        val now = System.currentTimeMillis()
        val cooldownElapsed = (now - lastSwitchAtMs) >= SWITCH_COOLDOWN_MS

        val shouldSwitch = when {
            bestProvider.id == activeId -> false
            !activeReachable -> true
            !bypassCooldown && !cooldownElapsed -> false
            // On network switch / startup, switch if meaningfully faster (25ms); during normal checks require 60ms margin
            bypassCooldown -> (activeResult!!.latencyMs - bestResult.latencyMs) >= 25L || activeResult.latencyMs >= SLOW_THRESHOLD_MS
            else -> (activeResult!!.latencyMs - bestResult.latencyMs) >= MIN_IMPROVEMENT_MS
        }

        if (shouldSwitch) {
            val reason = "$reasonPrefix → ${bestProvider.name} (${bestResult.latencyMs} ms)"
            lastSwitchAtMs = now
            onSwitchDns(bestProvider, reason)
            _status.update {
                it.copy(
                    isChecking = false,
                    activeLatencyMs = bestResult.latencyMs,
                    consecutiveSlowCount = 0,
                    statusLine = "Auto-tuned • ${bestResult.latencyMs} ms",
                    lastSwitchReason = reason
                )
            }
        } else {
            val activeMs = activeResult?.latencyMs?.takeIf { it > 0 } ?: bestResult.latencyMs
            _status.update {
                it.copy(
                    isChecking = false,
                    activeLatencyMs = activeMs,
                    consecutiveSlowCount = 0,
                    statusLine = "Optimal • $activeMs ms (auto-failover > ${SLOW_THRESHOLD_MS}ms)"
                )
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        if (networkCallbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        _status.update { it.copy(isMonitoring = false, isChecking = false) }
    }
}
