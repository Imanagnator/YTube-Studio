let adBlockEnabled = true;
let backgroundAudioEnabled = true;
let preferredQuality = "auto";
let studioZoomPercent = 85;
let port = null;

async function exportYouTubeCookiesTxt() {
  if (!browser.cookies || !browser.cookies.getAll) return "";
  try {
    const [ytCookies, googleCookies] = await Promise.all([
      browser.cookies.getAll({ domain: "youtube.com" }).catch(() => []),
      browser.cookies.getAll({ domain: "google.com" }).catch(() => [])
    ]);
    const merged = [...ytCookies, ...googleCookies];
    if (!merged.length) return "";
    const lines = ["# Netscape HTTP Cookie File", "# Exported by YTube Studio GeckoView"];
    const seen = new Set();
    for (const c of merged) {
      const key = `${c.domain}|${c.path}|${c.name}`;
      if (seen.has(key)) continue;
      seen.add(key);
      const includeSubdomains = c.domain.startsWith(".") ? "TRUE" : "FALSE";
      const secure = c.secure ? "TRUE" : "FALSE";
      const expiry = c.expirationDate ? Math.floor(c.expirationDate) : 0;
      lines.push(`${c.domain}\t${includeSubdomains}\t${c.path}\t${secure}\t${expiry}\t${c.name}\t${c.value}`);
    }
    return lines.join("\n");
  } catch (e) {
    return "";
  }
}

function postNative(payload) {
  if (!port) return;
  try {
    port.postMessage(payload);
  } catch (_) {}
}

function broadcastToTabs(payload) {
  if (!browser.tabs) return;
  browser.tabs.query({}).then((tabs) => {
    for (const tab of tabs) {
      browser.tabs.sendMessage(tab.id, payload).catch(() => {});
    }
  }).catch(() => {});
}

function connectNative() {
  try {
    port = browser.runtime.connectNative("tubedeck");
    port.onMessage.addListener(async (msg) => {
      if (!msg) return;
      if (typeof msg.adBlockEnabled === "boolean") {
        adBlockEnabled = msg.adBlockEnabled;
      }
      if (typeof msg.backgroundAudioEnabled === "boolean") {
        backgroundAudioEnabled = msg.backgroundAudioEnabled;
      }
      if (typeof msg.preferredQuality === "string") {
        preferredQuality = msg.preferredQuality;
      }
      if (typeof msg.studioZoomPercent === "number") {
        studioZoomPercent = msg.studioZoomPercent;
      }

      if (msg.type === "setQuality" && typeof msg.quality === "string") {
        preferredQuality = msg.quality;
        broadcastToTabs({
          type: "setQuality",
          quality: msg.quality,
          forceRefresh: true
        });
      } else if (msg.type === "queryQuality") {
        broadcastToTabs({ type: "queryQuality" });
      } else if (msg.type === "exportCookies") {
        const cookiesTxt = await exportYouTubeCookiesTxt();
        postNative({ type: "cookiesExport", cookiesTxt });
      } else if (msg.type === "triggerStudioUpload") {
        broadcastToTabs({ type: "triggerStudioUpload" });
      } else if (msg.type === "updateSettings") {
        broadcastToTabs({
          type: "settings",
          adBlockEnabled,
          backgroundAudioEnabled,
          preferredQuality,
          studioZoomPercent
        });
      }
    });
    port.onDisconnect.addListener(() => {
      port = null;
    });
  } catch (e) {
    port = null;
  }
}

if (browser.runtime && browser.runtime.onMessage) {
  browser.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (!msg) return false;
    if (msg.type === "getConfig") {
      sendResponse({ adBlockEnabled, backgroundAudioEnabled, preferredQuality, studioZoomPercent });
      return true;
    }
    if (msg.type === "adBlocked") {
      postNative({ type: "adBlocked" });
      return false;
    }
    if (msg.type === "qualityState") {
      postNative({
        type: "qualityState",
        currentQuality: msg.currentQuality || "auto",
        resolvedQuality: msg.resolvedQuality || "",
        availableQualities: Array.isArray(msg.availableQualities) ? msg.availableQualities : [],
        videoUrl: msg.videoUrl || ""
      });
      return false;
    }
    if (msg.type === "openDownloadSheet") {
      exportYouTubeCookiesTxt().then((cookiesTxt) => {
        postNative({
          type: "openDownloadSheet",
          url: msg.url || "",
          title: msg.title || "",
          cookiesTxt
        });
      });
      return false;
    }
    return false;
  });
}

connectNative();
