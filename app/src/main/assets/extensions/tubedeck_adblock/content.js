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

  // Lightweight CSP-safe YouTube Ad-Shield Failsafe (backs up uBlock Origin on first boot & mobile feed)
  let lastAdSkipAt = 0;

  function syncCompanionAdShieldCss() {
    if (location.hostname === "studio.youtube.com") return;
    const styleId = "ytstudio-adshield-css";
    const existing = document.getElementById(styleId);
    if (!config.adBlockEnabled) {
      if (existing) existing.remove();
      return;
    }
    if (existing || !document.documentElement) return;
    const style = document.createElement("style");
    style.id = styleId;
    style.textContent = [
      "ytm-promoted-sparkles-web-renderer,",
      "ytm-promoted-video-renderer,",
      "ytm-in-feed-ad-layout-renderer,",
      "ytm-ad-slot-renderer,",
      "ad-slot-renderer,",
      "ytd-ad-slot-renderer,",
      "ytd-promoted-sparkles-web-renderer,",
      "ytd-banner-promo-renderer,",
      "ytm-companion-slot,",
      ".ytp-ad-overlay-container,",
      ".ytp-ad-text-overlay,",
      ".video-ads .ytp-ad-module,",
      "#player-ads { display: none !important; }"
    ].join("\n");
    (document.head || document.documentElement).appendChild(style);
  }

  function sweepActiveVideoAdIfAny() {
    if (!config.adBlockEnabled || location.hostname === "studio.youtube.com") return;
    const playerEl =
      document.getElementById("movie_player") ||
      document.querySelector(".html5-video-player");
    if (!playerEl) return;

    const isAdShowing =
      playerEl.classList.contains("ad-showing") ||
      playerEl.classList.contains("ad-interrupting") ||
      Boolean(document.querySelector(".ytp-ad-player-overlay, .ytp-ad-player-overlay-layout"));
    if (!isAdShowing) return;

    const video = playerEl.querySelector("video.html5-main-video, video");
    if (video) {
      try {
        video.muted = true;
        if (Number.isFinite(video.duration) && video.duration > 0 && video.duration < 1800) {
          video.currentTime = video.duration;
        } else {
          video.playbackRate = 16;
        }
      } catch (_) {}
    }

    const skipSelectors = [
      ".ytp-skip-ad-button",
      ".ytp-ad-skip-button",
      ".ytp-ad-skip-button-modern",
      "button.ytp-ad-skip-button-modern",
      ".ytp-ad-skip-button-container button"
    ];
    for (const sel of skipSelectors) {
      const btn = document.querySelector(sel);
      if (btn && typeof btn.click === "function") {
        try { btn.click(); } catch (_) {}
      }
    }

    const now = Date.now();
    if (now - lastAdSkipAt > 3000) {
      lastAdSkipAt = now;
      browser.runtime.sendMessage({ type: "adBlocked" }).catch(() => {});
    }
  }

  // Built-in YTube Studio Creator Enhancer (Desktop Studio SEO char badges + HD Thumbnail & Tag Copy)
  function extractCurrentVideoId() {
    try {
      const url = new URL(location.href);
      const v = url.searchParams.get("v");
      if (v && v.length === 11) return v;
      const m = location.pathname.match(/\/(?:shorts|video|embed|live)\/([A-Za-z0-9_-]{11})/);
      if (m && m[1]) return m[1];
    } catch (_) {}
    return null;
  }

  function refreshStudioCreatorBar() {
    if (location.hostname !== "studio.youtube.com") return;
    const vid = extractCurrentVideoId();
    const existing = document.getElementById("ytstudio-creator-tools-bar");
    if (!vid) {
      if (existing) existing.remove();
      return;
    }

    // Live Title & Description SEO character count badge on Studio Video Edit page
    const titleBox = document.querySelector("#title-textarea #textbox, [aria-label*='Add a title' i]");
    const descBox = document.querySelector("#description-textarea #textbox, [aria-label*='Tell viewers' i]");
    const titleLen = titleBox ? (titleBox.textContent || "").trim().length : -1;
    const descLen = descBox ? (descBox.textContent || "").trim().length : -1;

    if (!existing && document.body) {
      const bar = document.createElement("div");
      bar.id = "ytstudio-creator-tools-bar";
      bar.style.cssText = [
        "position:fixed",
        "bottom:14px",
        "right:14px",
        "z-index:2147483640",
        "display:flex",
        "align-items:center",
        "gap:6px",
        "padding:6px 10px",
        "border-radius:12px",
        "background:rgba(13,15,20,0.92)",
        "border:1px solid rgba(255,255,255,0.16)",
        "color:#F5F7FA",
        "font:600 11px/1.2 system-ui,-apple-system,sans-serif",
        "box-shadow:0 4px 16px rgba(0,0,0,0.45)",
        "backdrop-filter:blur(8px)"
      ].join(";");

      const seoSpan = document.createElement("span");
      seoSpan.id = "ytstudio-seo-counter";
      seoSpan.style.color = "#00E676";

      const thumbBtn = document.createElement("button");
      thumbBtn.textContent = "HD Thumb";
      thumbBtn.style.cssText = "background:#E50914;color:#fff;border:none;border-radius:7px;padding:4px 8px;font-weight:700;font-size:11px;cursor:pointer;";
      thumbBtn.onclick = () => {
        const curVid = extractCurrentVideoId();
        if (!curVid) return;
        const a = document.createElement("a");
        a.href = `https://i.ytimg.com/vi/${curVid}/maxresdefault.jpg`;
        a.target = "_blank";
        a.download = `${curVid}_thumbnail.jpg`;
        a.click();
      };

      const tagsBtn = document.createElement("button");
      tagsBtn.textContent = "Copy Tags";
      tagsBtn.style.cssText = "background:#1E2430;color:#00E5FF;border:1px solid rgba(0,229,255,0.35);border-radius:7px;padding:4px 8px;font-weight:700;font-size:11px;cursor:pointer;";
      tagsBtn.onclick = () => {
        const chips = Array.from(document.querySelectorAll("ytcp-chip-bar ytcp-chip #chip-text, #tags-container ytcp-chip #chip-text"))
          .map((el) => (el.textContent || "").trim())
          .filter(Boolean);
        const text = chips.join(", ");
        if (text && navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(() => {
            tagsBtn.textContent = `Copied (${chips.length})`;
            setTimeout(() => { tagsBtn.textContent = "Copy Tags"; }, 1800);
          }).catch(() => {});
        } else {
          tagsBtn.textContent = "No Tags Found";
          setTimeout(() => { tagsBtn.textContent = "Copy Tags"; }, 1600);
        }
      };

      bar.appendChild(seoSpan);
      bar.appendChild(thumbBtn);
      bar.appendChild(tagsBtn);
      document.body.appendChild(bar);
    }

    const counterEl = document.getElementById("ytstudio-seo-counter");
    if (counterEl) {
      if (titleLen >= 0) {
        const tColor = titleLen >= 40 && titleLen <= 70 ? "#00E676" : (titleLen > 95 ? "#FF5252" : "#FFB300");
        counterEl.style.color = tColor;
        counterEl.textContent = descLen >= 0
          ? `Title ${titleLen}/100 • Desc ${descLen}/5000`
          : `Title ${titleLen}/100`;
      } else {
        counterEl.style.color = "#00E5FF";
        counterEl.textContent = "YTube Studio Tools";
      }
    }
  }

  function applyAllConfig(forceQualityRefresh = false) {
    installBackgroundVisibilityHook();
    installBuiltInDownloadButtonHook();
    injectPreconnectHints();
    syncCompanionAdShieldCss();
    sweepActiveVideoAdIfAny();
    applyStudioZoom();
    refreshStudioCreatorBar();
    applyQuality(config.preferredQuality, forceQualityRefresh);
  }

  if (document.documentElement) {
    installBackgroundVisibilityHook();
    installBuiltInDownloadButtonHook();
    injectPreconnectHints();
    syncCompanionAdShieldCss();
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

  // Fast ad-sweep guard (only acts if .ad-showing ever slips through while uBlock warms up)
  setInterval(() => {
    if (config.adBlockEnabled && location.hostname !== "studio.youtube.com") {
      if (location.pathname.startsWith("/watch") || location.pathname.startsWith("/shorts/")) {
        sweepActiveVideoAdIfAny();
      }
    }
  }, 900);

  // Lightweight periodic guard on watch/shorts pages only so feed scrolling stays fast
  setInterval(() => {
    const urlChanged = location.href !== lastUrl;
    if (urlChanged) {
      lastUrl = location.href;
      lastReportedSignature = "";
    }
    if (location.hostname === "studio.youtube.com") {
      refreshStudioCreatorBar();
      return;
    }
    syncCompanionAdShieldCss();
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
