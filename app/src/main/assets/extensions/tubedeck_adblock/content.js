(() => {
  if (window.__ytMaxContentReady) return;
  window.__ytMaxContentReady = true;

  let config = {
    adBlockEnabled: true,
    backgroundAudioEnabled: true,
    preferredQuality: "auto",
    studioZoomPercent: 85
  };

  let visibilityHooked = false;
  let lastUrl = location.href;
  let lastReportedSignature = "";

  // Ordered highest -> lowest so we can always step down to the nearest available quality
  const QUALITY_ORDER = [
    "hd2160",
    "hd1440",
    "hd1080",
    "hd720",
    "large",
    "medium",
    "small",
    "tiny"
  ];

  const LABEL_TO_CODE = {
    "2160p": "hd2160",
    "1440p": "hd1440",
    "1080p": "hd1080",
    "720p": "hd720",
    "480p": "large",
    "360p": "medium",
    "240p": "small",
    "144p": "tiny",
    "auto": "auto"
  };

  const CODE_TO_LABEL = {
    "highres": "2160p",
    "hd2160": "2160p",
    "hd1440": "1440p",
    "hd1080": "1080p",
    "hd720": "720p",
    "large": "480p",
    "medium": "360p",
    "small": "240p",
    "tiny": "144p",
    "auto": "Auto",
    "unknown": "Auto"
  };

  const CODE_TO_HEIGHT = {
    "hd2160": 2160,
    "hd1440": 1440,
    "hd1080": 1080,
    "hd720": 720,
    "large": 480,
    "medium": 360,
    "small": 240,
    "tiny": 144
  };

  function unwrap(obj) {
    if (!obj) return null;
    try {
      return obj.wrappedJSObject || obj;
    } catch (_) {
      return obj;
    }
  }

  // CSP-safe background visibility unlock using Firefox's wrappedJSObject & exportFunction
  function installBackgroundVisibilityHook() {
    if (visibilityHooked) return;
    visibilityHooked = true;

    try {
      const rawDoc = unwrap(document);
      const rawWin = unwrap(window);
      if (rawDoc && rawWin) {
        const docProto = unwrap(Document.prototype) || Object.getPrototypeOf(rawDoc);
        const origHiddenGetter = docProto
          ? Object.getOwnPropertyDescriptor(docProto, "hidden")?.get
          : null;
        const origVisGetter = docProto
          ? Object.getOwnPropertyDescriptor(docProto, "visibilityState")?.get
          : null;

        const makeGetter = (fn) => {
          return typeof exportFunction === "function"
            ? exportFunction(fn, rawWin)
            : fn;
        };

        try {
          Object.defineProperty(rawDoc, "hidden", {
            configurable: true,
            get: makeGetter(() => {
              if (config.backgroundAudioEnabled) return false;
              try {
                return origHiddenGetter ? Boolean(origHiddenGetter.call(rawDoc)) : false;
              } catch (_) {
                return false;
              }
            })
          });
        } catch (_) {}

        try {
          Object.defineProperty(rawDoc, "visibilityState", {
            configurable: true,
            get: makeGetter(() => {
              if (config.backgroundAudioEnabled) return "visible";
              try {
                return origVisGetter ? String(origVisGetter.call(rawDoc)) : "visible";
              } catch (_) {
                return "visible";
              }
            })
          });
        } catch (_) {}
      }
    } catch (_) {}

    const blockVisibilityEvent = (e) => {
      if (config.backgroundAudioEnabled) {
        e.stopImmediatePropagation();
      }
    };
    window.addEventListener("visibilitychange", blockVisibilityEvent, true);
    document.addEventListener("visibilitychange", blockVisibilityEvent, true);
    window.addEventListener("webkitvisibilitychange", blockVisibilityEvent, true);
    window.addEventListener("pagehide", blockVisibilityEvent, true);
  }

  function findRawPlayer() {
    const el =
      document.getElementById("movie_player") ||
      document.querySelector(".html5-video-player") ||
      document.querySelector("ytm-player .html5-video-player");
    return unwrap(el);
  }

  function persistQualityToStorage(code) {
    try {
      if (!code || code === "auto") {
        localStorage.removeItem("yt-player-quality");
        sessionStorage.removeItem("yt-player-quality");
        return;
      }
      const now = Date.now();
      const exp = now + 30 * 24 * 60 * 60 * 1000;
      const height = CODE_TO_HEIGHT[code] || 720;
      const payload = JSON.stringify({
        data: JSON.stringify({ quality: height, previousQuality: height }),
        expiration: exp,
        creation: now
      });
      localStorage.setItem("yt-player-quality", payload);
      sessionStorage.setItem("yt-player-quality", payload);
    } catch (_) {}
  }

  function readAvailableLevels(player) {
    if (!player) return [];
    try {
      if (typeof player.getAvailableQualityLevels === "function") {
        const rawLevels = player.getAvailableQualityLevels();
        if (rawLevels && rawLevels.length) {
          const out = [];
          for (let i = 0; i < rawLevels.length; i++) {
            const lvl = String(rawLevels[i] || "");
            if (lvl && lvl !== "auto" && !out.includes(lvl)) {
              out.push(lvl);
            }
          }
          return out;
        }
      }
    } catch (_) {}
    return [];
  }

  function readCurrentQualityLabel(player) {
    if (!player) return "Auto";
    try {
      if (typeof player.getPlaybackQuality === "function") {
        const code = String(player.getPlaybackQuality() || "");
        if (CODE_TO_LABEL[code]) return CODE_TO_LABEL[code];
      }
    } catch (_) {}
    // Fallback to actual <video> height if player API hasn't updated yet
    try {
      const video = document.querySelector("video.html5-main-video, video");
      if (video && video.videoHeight > 0) {
        const h = video.videoHeight;
        if (h >= 2000) return "2160p";
        if (h >= 1400) return "1440p";
        if (h >= 1000) return "1080p";
        if (h >= 680) return "720p";
        if (h >= 440) return "480p";
        if (h >= 320) return "360p";
        if (h >= 210) return "240p";
        return "144p";
      }
    } catch (_) {}
    return "Auto";
  }

  function resolveTargetCode(requestedCode, availableLevels) {
    if (!requestedCode || requestedCode === "auto") return "auto";
    if (!availableLevels || availableLevels.length === 0) return requestedCode;
    if (availableLevels.includes(requestedCode)) return requestedCode;

    // Pick closest available quality (prefer next lower tier, otherwise highest available)
    const reqIdx = QUALITY_ORDER.indexOf(requestedCode);
    if (reqIdx >= 0) {
      for (let i = reqIdx + 1; i < QUALITY_ORDER.length; i++) {
        if (availableLevels.includes(QUALITY_ORDER[i])) {
          return QUALITY_ORDER[i];
        }
      }
      for (let i = reqIdx - 1; i >= 0; i--) {
        if (availableLevels.includes(QUALITY_ORDER[i])) {
          return QUALITY_ORDER[i];
        }
      }
    }
    return availableLevels[0];
  }

  function reportQualityState(player, resolvedCode, forceSend = false) {
    const availableCodes = readAvailableLevels(player);
    const availableLabels = availableCodes
      .map((c) => CODE_TO_LABEL[c])
      .filter((v, idx, arr) => v && v !== "Auto" && arr.indexOf(v) === idx);
    const currentLabel = readCurrentQualityLabel(player);
    const resolvedLabel = resolvedCode ? (CODE_TO_LABEL[resolvedCode] || resolvedCode) : "";
    const sig = `${currentLabel}|${resolvedLabel}|${availableLabels.join(",")}|${location.pathname}${location.search}`;
    if (!forceSend && sig === lastReportedSignature) return;
    lastReportedSignature = sig;

    browser.runtime.sendMessage({
      type: "qualityState",
      currentQuality: currentLabel,
      resolvedQuality: resolvedLabel,
      availableQualities: availableLabels,
      videoUrl: location.href
    }).catch(() => {});
  }

  function applyQuality(preferredLabel, forceRefresh = false) {
    const key = (preferredLabel || "auto").toLowerCase();
    const requestedCode = LABEL_TO_CODE[key] || "auto";
    persistQualityToStorage(requestedCode);

    const player = findRawPlayer();
    if (!player) {
      reportQualityState(null, requestedCode, forceRefresh);
      return;
    }

    const availableLevels = readAvailableLevels(player);
    const resolvedCode = resolveTargetCode(requestedCode, availableLevels);

    try {
      if (resolvedCode === "auto") {
        if (typeof player.setPlaybackQualityRange === "function" && availableLevels.length > 0) {
          // Restore full ABR range (lowest -> highest)
          player.setPlaybackQualityRange(
            availableLevels[availableLevels.length - 1],
            availableLevels[0]
          );
        }
        if (typeof player.setPlaybackQuality === "function") {
          player.setPlaybackQuality("default");
        }
      } else {
        if (typeof player.setPlaybackQualityRange === "function") {
          player.setPlaybackQualityRange(resolvedCode, resolvedCode);
        }
        if (typeof player.setPlaybackQuality === "function") {
          player.setPlaybackQuality(resolvedCode);
        }

        // Flush stale low-res buffer immediately when user explicitly picks a new quality
        if (forceRefresh && typeof player.getCurrentTime === "function" && typeof player.seekTo === "function") {
          const currentQ = typeof player.getPlaybackQuality === "function" ? player.getPlaybackQuality() : "";
          const t = Number(player.getCurrentTime()) || 0;
          if (currentQ !== resolvedCode && t > 0.4) {
            player.seekTo(t, true);
          }
        }
      }
    } catch (_) {}

    reportQualityState(player, resolvedCode, forceRefresh);
  }

  function applyStudioZoom() {
    if (location.hostname === "studio.youtube.com" && document.documentElement) {
      const pct = Number(config.studioZoomPercent) || 85;
      document.documentElement.style.zoom = String(pct / 100);
    }
  }

  function triggerAppDownloadSheet() {
    browser.runtime.sendMessage({
      type: "openDownloadSheet",
      url: location.href,
      title: document.title.replace(/\s*-\s*YouTube$/i, "").trim()
    }).catch(() => {});
  }

  // Intercept YouTube's own built-in Download button so tapping it launches YTube Studio's Download Manager
  function installBuiltInDownloadButtonHook() {
    if (window.__ytMaxDownloadBtnHooked) return;
    window.__ytMaxDownloadBtnHooked = true;

    document.addEventListener("click", (e) => {
      if (location.hostname === "studio.youtube.com") return;
      if (!location.pathname.startsWith("/watch") && !location.pathname.startsWith("/shorts/")) return;

      const target = e.target;
      if (!target || typeof target.closest !== "function") return;

      const dlRenderer = target.closest(
        "ytm-download-button-renderer, ytd-download-button-renderer, download-button-view-model, [data-testid*='download' i]"
      );
      const btnEl = dlRenderer || target.closest("button, a, [role='button']");
      if (!btnEl) return;

      const aria = (btnEl.getAttribute("aria-label") || "").trim().toLowerCase();
      const text = (btnEl.textContent || "").trim().toLowerCase();
      const isYouTubeDownloadBtn =
        Boolean(dlRenderer) ||
        aria === "download" ||
        aria.startsWith("download ") ||
        text === "download";

      if (isYouTubeDownloadBtn) {
        e.preventDefault();
        e.stopPropagation();
        if (typeof e.stopImmediatePropagation === "function") {
          e.stopImmediatePropagation();
        }
        triggerAppDownloadSheet();
      }
    }, true);
  }

  function injectPreconnectHints() {
    if (window.__ytStudioPreconnectDone || !document.head) return;
    window.__ytStudioPreconnectDone = true;
    const origins = [
      "https://i.ytimg.com",
      "https://yt3.ggpht.com",
      "https://fonts.gstatic.com"
    ];
    for (const href of origins) {
      const link = document.createElement("link");
      link.rel = "preconnect";
      link.href = href;
      link.crossOrigin = "anonymous";
      document.head.appendChild(link);
    }
  }

  function applyAllConfig(forceQualityRefresh = false) {
    installBackgroundVisibilityHook();
    installBuiltInDownloadButtonHook();
    injectPreconnectHints();
    applyStudioZoom();
    applyQuality(config.preferredQuality, forceQualityRefresh);
  }

  if (document.documentElement) {
    installBackgroundVisibilityHook();
    installBuiltInDownloadButtonHook();
    injectPreconnectHints();
    applyStudioZoom();
  }

  document.addEventListener("DOMContentLoaded", () => {
    applyAllConfig(false);
  }, { once: true });

  window.addEventListener("yt-navigate-finish", () => {
    setTimeout(() => applyAllConfig(false), 400);
    setTimeout(() => applyAllConfig(false), 1600);
  });

  browser.runtime.sendMessage({ type: "getConfig" }).then((res) => {
    if (res) {
      config = { ...config, ...res };
      applyAllConfig(false);
    }
  }).catch(() => {});

  browser.runtime.onMessage.addListener((msg) => {
    if (!msg) return;
    if (msg.type === "setQuality") {
      config.preferredQuality = msg.quality || "auto";
      applyQuality(config.preferredQuality, Boolean(msg.forceRefresh));
      // Re-verify shortly after seek/switch so live badge reflects the real playing tier
      setTimeout(() => reportQualityState(findRawPlayer(), LABEL_TO_CODE[config.preferredQuality.toLowerCase()], true), 1200);
      setTimeout(() => reportQualityState(findRawPlayer(), LABEL_TO_CODE[config.preferredQuality.toLowerCase()], true), 3000);
    } else if (msg.type === "queryQuality") {
      reportQualityState(findRawPlayer(), LABEL_TO_CODE[(config.preferredQuality || "auto").toLowerCase()], true);
    } else if (msg.type === "settings") {
      config = { ...config, ...msg };
      applyAllConfig(false);
    }
  });

  // Lightweight periodic guard on watch/shorts pages only so feed scrolling stays fast
  setInterval(() => {
    const urlChanged = location.href !== lastUrl;
    if (urlChanged) {
      lastUrl = location.href;
      lastReportedSignature = "";
    }
    const isVideoPage =
      location.pathname.startsWith("/watch") || location.pathname.startsWith("/shorts/");
    if (!isVideoPage) return;

    const player = findRawPlayer();
    if (player) {
      if (config.preferredQuality && config.preferredQuality.toLowerCase() !== "auto") {
        const requestedCode = LABEL_TO_CODE[config.preferredQuality.toLowerCase()];
        const available = readAvailableLevels(player);
        const resolved = resolveTargetCode(requestedCode, available);
        const currentCode = typeof player.getPlaybackQuality === "function"
          ? String(player.getPlaybackQuality() || "")
          : "";
        if (urlChanged || (resolved && currentCode && currentCode !== resolved && currentCode !== "unknown")) {
          applyQuality(config.preferredQuality, false);
        } else {
          reportQualityState(player, resolved, false);
        }
      } else {
        reportQualityState(player, "auto", false);
      }
    }
  }, 4000);
})();
