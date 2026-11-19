(() => {
  if (window.__ytStudioProInstalled) return;
  window.__ytStudioProInstalled = true;

  let cfg = {
    enabled: true,
    autoCollapseSidebar: true,
    focusMode: false,
    seoHudEnabled: true,
    privacyBlur: false,
    customTemplate: ""
  };

  let sidebarCollapsedOnce = false;
  let hudCollapsed = false;
  let lastMetricsSig = "";

  const TEMPLATES = {
    gaming: [
      "Welcome back! In today's video, we jump into [Game Name] and cover the best strategies, highlights, and tips.",
      "",
      "⏱️ TIMESTAMPS:",
      "0:00 Intro",
      "0:45 Gameplay Highlights",
      "4:30 Key Tips & Breakdown",
      "8:00 Final Thoughts",
      "",
      "👍 Like & Subscribe for more gaming content!",
      "#gaming #gameplay #youtube"
    ].join("\n"),
    tutorial: [
      "In this step-by-step tutorial, you'll learn how to [Topic] quickly and clearly.",
      "",
      "⏱️ CHAPTERS:",
      "0:00 Overview",
      "0:35 Step 1 - Setup",
      "2:15 Step 2 - Walkthrough",
      "5:00 Pro Tips & Summary",
      "",
      "🔗 Useful Links:",
      "• Resource 1: ",
      "",
      "💬 Drop your questions in the comments!",
      "#tutorial #howto #guide"
    ].join("\n"),
    shorts: [
      "Watch till the end! 🔥",
      "",
      "Subscribe for daily Shorts & creator tips.",
      "#shorts #viral #trending"
    ].join("\n"),
    vlog: [
      "Thanks for hanging out with me today! Here's everything that happened behind the scenes.",
      "",
      "⏱️ MOMENTS:",
      "0:00 Morning Start",
      "1:30 Main Highlight",
      "4:00 Wrap Up",
      "",
      "🔔 Subscribe and turn on notifications!",
      "#vlog #dailyvlog #creator"
    ].join("\n")
  };

  function toastNative(message) {
    browser.runtime.sendMessage({ type: "studioProToast", message }).catch(() => {});
  }

  // Recursive Shadow-DOM-piercing query selector for YouTube Studio Polymer components
  function queryDeep(selector, root = document) {
    if (!root) return null;
    try {
      const direct = root.querySelector(selector);
      if (direct) return direct;
    } catch (_) {}
    const nodes = root.querySelectorAll ? root.querySelectorAll("*") : [];
    for (let i = 0; i < nodes.length; i++) {
      const el = nodes[i];
      if (el && el.shadowRoot) {
        const found = queryDeep(selector, el.shadowRoot);
        if (found) return found;
      }
    }
    return null;
  }

  function queryDeepAll(selector, root = document, acc = []) {
    if (!root) return acc;
    try {
      const found = root.querySelectorAll(selector);
      for (let i = 0; i < found.length; i++) acc.push(found[i]);
    } catch (_) {}
    const nodes = root.querySelectorAll ? root.querySelectorAll("*") : [];
    for (let i = 0; i < nodes.length; i++) {
      const el = nodes[i];
      if (el && el.shadowRoot) {
        queryDeepAll(selector, el.shadowRoot, acc);
      }
    }
    return acc;
  }

  function extractVideoId() {
    try {
      const m = location.pathname.match(/\/video\/([A-Za-z0-9_-]{11})/);
      if (m && m[1]) return m[1];
      const url = new URL(location.href);
      const v = url.searchParams.get("v");
      if (v && v.length === 11) return v;
    } catch (_) {}
    return "";
  }

  function syncStudioProStyles() {
    const styleId = "ytube-studio-pro-css";
    const existing = document.getElementById(styleId);
    if (!cfg.enabled) {
      if (existing) existing.remove();
      return;
    }
    const rules = [
      // Never distort Shadow DOM hitboxes with CSS zoom
      "html { zoom: normal !important; }",
      // Crisp dark scrollbar & clean touch ergonomics inside PC Studio on mobile
      "ytcp-uploads-dialog tp-yt-paper-dialog { max-width: 96vw !important; }",
      "ytcp-button, ytcp-icon-button { touch-action: manipulation; }"
    ];

    if (cfg.focusMode) {
      rules.push(
        "ytcd-news-item, ytcp-news-card, ytcd-ideas-card, ytcd-creator-insider-card, " +
        "ytcd-whats-new-card, ytcd-help-card, ytcd-channel-facts-item[data-promo], " +
        "[test-id='news-card'], [test-id='creator-insider-card'] { display: none !important; }"
      );
    }

    if (cfg.privacyBlur) {
      rules.push(
        ".metric-value-big, .metric-value, #metric-total, " +
        "ytcd-channel-facts-item .metric-value, " +
        "yta-key-metric-block #metric-total, " +
        "yta-key-metric-block .metric-total, " +
        "ytcp-video-row .tablecell-views, " +
        "ytcp-video-row .tablecell-comments, " +
        "ytcd-video-snapshot-item .metric-value { filter: blur(7px) !important; transition: filter 0.2s ease; }",
        ".metric-value-big:hover, .metric-value:hover, #metric-total:hover { filter: none !important; }"
      );
    }

    const cssText = rules.join("\n");
    if (existing) {
      if (existing.textContent !== cssText) existing.textContent = cssText;
    } else if (document.head || document.documentElement) {
      const style = document.createElement("style");
      style.id = styleId;
      style.textContent = cssText;
      (document.head || document.documentElement).appendChild(style);
    }
  }

  function applyAutoCollapseSidebar() {
    if (!cfg.enabled || !cfg.autoCollapseSidebar || sidebarCollapsedOnce) return;
    // Only auto-collapse if the desktop sidebar is currently expanded wide
    const navDrawer = queryDeep("ytcp-navigation-drawer, tp-yt-app-drawer");
    if (!navDrawer) return;
    const isCollapsed =
      navDrawer.hasAttribute("collapsed") ||
      navDrawer.getAttribute("collapsed") === "" ||
      navDrawer.classList.contains("collapsed");
    if (isCollapsed) {
      sidebarCollapsedOnce = true;
      return;
    }
    const toggleBtn = queryDeep("#menu-button, ytcp-icon-button#menu-button, button[aria-label='Collapse menu']");
    if (toggleBtn && typeof toggleBtn.click === "function") {
      try {
        toggleBtn.click();
        sidebarCollapsedOnce = true;
      } catch (_) {}
    }
  }

  function triggerSmartStudioUpload() {
    // 1. If the Upload dialog is already open, click "Select files" directly!
    const selectFilesBtn = queryDeep("#select-files-button, ytcp-button#select-files-button");
    if (selectFilesBtn && selectFilesBtn.offsetParent !== null) {
      selectFilesBtn.click();
      return;
    }
    // 2. Try clicking the hidden <input type="file"> inside an open uploads dialog
    const fileInput = queryDeep("ytcp-uploads-file-picker input[type='file'], input[name='Filedata']");
    if (fileInput && typeof fileInput.click === "function") {
      fileInput.click();
      return;
    }
    // 3. Try Studio's direct top-bar Upload icon
    const uploadIcon = queryDeep("#upload-icon, ytcp-icon-button#upload-icon, button[aria-label='Upload videos']");
    if (uploadIcon && typeof uploadIcon.click === "function") {
      uploadIcon.click();
      return;
    }
    // 4. Fall back to Create button -> Upload videos menu item
    const createBtn = queryDeep("#create-icon, ytcp-button#create-icon, button[aria-label='Create']");
    if (createBtn && typeof createBtn.click === "function") {
      createBtn.click();
      setTimeout(() => {
        const uploadItem = queryDeep("#text-item-0, tp-yt-paper-item[test-id='upload-beta'], tp-yt-paper-item[test-id*='upload']");
        if (uploadItem && typeof uploadItem.click === "function") {
          uploadItem.click();
        }
      }, 360);
    } else {
      toastNative("Open your Studio Dashboard first, then tap Upload");
    }
  }

  function findTitleElement() {
    return queryDeep(
      "#title-textarea #textbox, ytcp-social-suggestions-textbox#title-textarea #textbox, [aria-label*='Add a title' i]"
    );
  }

  function findDescriptionElement() {
    return queryDeep(
      "#description-textarea #textbox, ytcp-social-suggestions-textbox#description-textarea #textbox, [aria-label*='Tell viewers' i]"
    );
  }

  function readVideoTags() {
    const chips = queryDeepAll("ytcp-chip-bar ytcp-chip #chip-text, #tags-container ytcp-chip #chip-text");
    const seen = new Set();
    const tags = [];
    for (const el of chips) {
      const t = (el.textContent || "").trim();
      if (t && !seen.has(t.toLowerCase())) {
        seen.add(t.toLowerCase());
        tags.push(t);
      }
    }
    return tags;
  }

  function computeSeoMetrics() {
    const videoId = extractVideoId();
    const titleEl = findTitleElement();
    const descEl = findDescriptionElement();
    const isEditorPage = Boolean(titleEl || descEl || videoId);

    const titleText = titleEl ? (titleEl.textContent || "").trim() : "";
    const descText = descEl ? (descEl.textContent || "").trim() : "";
    const tags = readVideoTags();
    const tagsCharCount = tags.join(",").length;

    const titleLen = titleEl ? titleText.length : -1;
    const descLen = descEl ? descText.length : -1;
    const hasChapters = /(?:^|\s)0:00\b/.test(descText);
    const hashtags = (descText.match(/#[A-Za-z0-9_]+/g) || []);
    const hasCustomThumb = Boolean(queryDeep("ytcp-video-thumbnail-with-info img[src*='ytimg'], ytcp-thumbnails-compact-editor img[src]"));

    let score = 0;
    const tips = [];

    if (titleLen >= 0) {
      if (titleLen >= 40 && titleLen <= 70) {
        score += 30;
      } else if (titleLen >= 20 && titleLen <= 95) {
        score += 18;
        tips.push(titleLen < 40 ? "Expand title toward 45–65 chars" : "Keep core hook under 65 chars for mobile");
      } else {
        score += 5;
        tips.push("Aim for a clear 45–65 char title");
      }
    }

    if (descLen >= 0) {
      if (descLen >= 250) {
        score += 25;
      } else if (descLen >= 80) {
        score += 14;
        tips.push("Add 250+ chars to boost YouTube search");
      } else {
        score += 4;
        tips.push("Description is short — insert a Studio Pro template");
      }
      if (hasChapters) {
        score += 15;
      } else {
        tips.push("Add 0:00 chapters for Google/YouTube key moments");
      }
      if (hashtags.length >= 1 && hashtags.length <= 5) {
        score += 10;
      } else if (hashtags.length === 0) {
        tips.push("Add 2–3 relevant #hashtags");
      } else {
        score += 4;
        tips.push("Use 3–5 focused #hashtags instead of stuffing");
      }
    }

    if (tags.length >= 5 && tagsCharCount >= 120) {
      score += 20;
    } else if (tags.length >= 1) {
      score += 10;
      tips.push("Add a few more niche tags (use Smart Tags)");
    } else if (isEditorPage) {
      tips.push("No tags detected — tap Smart Tags to generate");
    }

    score = Math.min(100, Math.max(0, score));

    return {
      isEditorPage,
      videoId,
      titleText,
      titleLen,
      descLen,
      hasChapters,
      hashtagCount: hashtags.length,
      tagCount: tags.length,
      tagsCharCount,
      hasCustomThumb,
      seoScore: isEditorPage ? score : -1,
      topTip: tips[0] || (isEditorPage ? "Great metadata — ready to publish!" : "Open any video in Studio to inspect live SEO")
    };
  }

  function insertDescriptionTemplate(templateKeyOrText) {
    const descEl = findDescriptionElement();
    if (!descEl) {
      toastNative("Open a video's Details page first to insert a template");
      return;
    }
    const tpl = TEMPLATES[templateKeyOrText] ||
      (templateKeyOrText === "custom" ? cfg.customTemplate : templateKeyOrText) ||
      TEMPLATES.tutorial;
    if (!tpl.trim()) {
      toastNative("Save a Custom Template in Studio Pro first");
      return;
    }
    try {
      descEl.focus();
      const existing = (descEl.textContent || "").trim();
      const nextText = existing ? `${existing}\n\n${tpl}` : tpl;
      descEl.textContent = nextText;
      descEl.dispatchEvent(new InputEvent("input", { bubbles: true, cancelable: true, data: nextText }));
      descEl.dispatchEvent(new Event("change", { bubbles: true }));
      toastNative("Inserted template into video description");
      refreshHudAndReport(true);
    } catch (_) {
      copyToClipboard(tpl, "Template copied — paste into Description");
    }
  }

  function generateSmartTagsFromTitle() {
    const titleEl = findTitleElement();
    const rawTitle = titleEl ? (titleEl.textContent || "").trim() : "";
    if (!rawTitle) {
      toastNative("Enter a video title first to generate smart tags");
      return;
    }
    const stopWords = new Set([
      "the", "and", "for", "with", "that", "this", "from", "your", "have", "are", "was", "were",
      "you", "how", "what", "why", "into", "about", "best", "top", "new", "video", "youtube", "2024", "2025", "2026"
    ]);
    const cleaned = rawTitle
      .replace(/[^\p{L}\p{N}\s]/gu, " ")
      .replace(/\s+/g, " ")
      .trim();
    const words = cleaned.split(" ").filter((w) => w.length > 2);
    const keywords = words.filter((w) => !stopWords.has(w.toLowerCase()));

    const generated = [];
    const pushUnique = (val) => {
      const v = val.trim().toLowerCase();
      if (v.length >= 3 && !generated.includes(v)) generated.push(v);
    };

    pushUnique(cleaned);
    for (let i = 0; i < keywords.length - 1; i++) {
      pushUnique(`${keywords[i]} ${keywords[i + 1]}`);
    }
    for (let i = 0; i < keywords.length - 2; i++) {
      pushUnique(`${keywords[i]} ${keywords[i + 1]} ${keywords[i + 2]}`);
    }
    keywords.forEach((w) => pushUnique(w));
    if (keywords.length >= 1) {
      pushUnique(`${keywords[0]} tutorial`);
      pushUnique(`${keywords[0]} guide`);
      pushUnique(`${keywords[0]} tips`);
    }

    const csv = generated.slice(0, 18).join(", ");
    copyToClipboard(csv, `Copied ${Math.min(generated.length, 18)} smart tags — paste in Studio Tags`);
  }

  function copyCurrentVideoTags() {
    const tags = readVideoTags();
    if (!tags.length) {
      toastNative("No tags found on this page (expand Show More first)");
      return;
    }
    copyToClipboard(tags.join(", "), `Copied ${tags.length} tags (${tags.join(",").length}/500 chars)`);
  }

  function downloadHdThumbnail() {
    const vid = extractVideoId();
    if (!vid) {
      toastNative("Open a video Details page to grab its HD thumbnail");
      return;
    }
    const url = `https://i.ytimg.com/vi/${vid}/maxresdefault.jpg`;
    const a = document.createElement("a");
    a.href = url;
    a.target = "_blank";
    a.download = `${vid}_maxres_thumbnail.jpg`;
    a.click();
    toastNative("Opened HD thumbnail");
  }

  function copyToClipboard(text, successMsg) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => {
        toastNative(successMsg);
      }).catch(() => {
        fallbackCopy(text, successMsg);
      });
    } else {
      fallbackCopy(text, successMsg);
    }
  }

  function fallbackCopy(text, successMsg) {
    try {
      const ta = document.createElement("textarea");
      ta.value = text;
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      ta.remove();
      toastNative(successMsg);
    } catch (_) {
      toastNative("Could not access clipboard");
    }
  }

  function renderFloatingHud(metrics) {
    const hudId = "ytube-studio-pro-hud";
    const existing = document.getElementById(hudId);
    if (!cfg.enabled || !cfg.seoHudEnabled || !metrics.isEditorPage) {
      if (existing) existing.remove();
      return;
    }

    let hud = existing;
    if (!hud && document.body) {
      hud = document.createElement("div");
      hud.id = hudId;
      hud.style.cssText = [
        "position:fixed",
        "bottom:12px",
        "right:12px",
        "z-index:2147483640",
        "display:flex",
        "flex-direction:column",
        "gap:6px",
        "max-width:290px",
        "padding:8px 10px",
        "border-radius:14px",
        "background:rgba(13,16,22,0.94)",
        "border:1px solid rgba(0,229,255,0.34)",
        "color:#F5F7FA",
        "font:600 11px/1.3 system-ui,-apple-system,sans-serif",
        "box-shadow:0 8px 24px rgba(0,0,0,0.5)",
        "backdrop-filter:blur(10px)"
      ].join(";");
      document.body.appendChild(hud);
    }
    if (!hud) return;

    const scoreColor =
      metrics.seoScore >= 75 ? "#00E676" : (metrics.seoScore >= 45 ? "#FFB300" : "#FF5252");

    hud.innerHTML = "";

    const header = document.createElement("div");
    header.style.cssText = "display:flex;align-items:center;justify-content:space-between;gap:8px;cursor:pointer;";
    header.onclick = () => {
      hudCollapsed = !hudCollapsed;
      renderFloatingHud(computeSeoMetrics());
    };

    const badge = document.createElement("span");
    badge.style.cssText = `color:${scoreColor};font-weight:800;letter-spacing:0.3px;`;
    badge.textContent = `STUDIO PRO • SEO ${metrics.seoScore}/100`;

    const chevron = document.createElement("span");
    chevron.style.cssText = "color:#9AA5B4;font-size:10px;";
    chevron.textContent = hudCollapsed ? "▲ Expand" : "▼ Hide";

    header.appendChild(badge);
    header.appendChild(chevron);
    hud.appendChild(header);

    if (!hudCollapsed) {
      const stats = document.createElement("div");
      stats.style.cssText = "color:#C5D0DF;font-weight:500;font-size:10.5px;";
      const parts = [];
      if (metrics.titleLen >= 0) parts.push(`Title ${metrics.titleLen}/100`);
      if (metrics.descLen >= 0) parts.push(`Desc ${metrics.descLen}/5000`);
      parts.push(`Tags ${metrics.tagsCharCount}/500`);
      stats.textContent = parts.join(" • ");
      hud.appendChild(stats);

      const tip = document.createElement("div");
      tip.style.cssText = "color:#8BE9FD;font-weight:500;font-size:10.5px;";
      tip.textContent = metrics.topTip;
      hud.appendChild(tip);

      const btnRow = document.createElement("div");
      btnRow.style.cssText = "display:flex;flex-wrap:wrap;gap:5px;margin-top:2px;";

      const makeBtn = (label, bg, fg, onClick) => {
        const b = document.createElement("button");
        b.type = "button";
        b.textContent = label;
        b.style.cssText = `background:${bg};color:${fg};border:none;border-radius:7px;padding:5px 8px;font-weight:700;font-size:10.5px;cursor:pointer;`;
        b.onclick = (e) => {
          e.stopPropagation();
          onClick();
        };
        return b;
      };

      btnRow.appendChild(makeBtn("Smart Tags", "#0E2A36", "#00E5FF", generateSmartTagsFromTitle));
      btnRow.appendChild(makeBtn("Copy Tags", "#1F2633", "#E5ECF4", copyCurrentVideoTags));
      btnRow.appendChild(makeBtn("+Template", "#1A2E22", "#00E676", () => insertDescriptionTemplate(cfg.customTemplate ? "custom" : "tutorial")));
      if (metrics.videoId) {
        btnRow.appendChild(makeBtn("HD Thumb", "#3B1319", "#FF6B77", downloadHdThumbnail));
      }
      hud.appendChild(btnRow);
    }
  }

  function refreshHudAndReport(force = false) {
    syncStudioProStyles();
    applyAutoCollapseSidebar();
    const metrics = computeSeoMetrics();
    renderFloatingHud(metrics);

    const sig = `${metrics.isEditorPage}|${metrics.videoId}|${metrics.seoScore}|${metrics.titleLen}|${metrics.descLen}|${metrics.tagCount}|${metrics.tagsCharCount}|${metrics.topTip}`;
    if (force || sig !== lastMetricsSig) {
      lastMetricsSig = sig;
      browser.runtime.sendMessage({
        type: "studioProMetrics",
        ...metrics
      }).catch(() => {});
    }
  }

  browser.runtime.sendMessage({ type: "getStudioProConfig" }).then((res) => {
    if (res) {
      cfg = { ...cfg, ...res };
      refreshHudAndReport(true);
    }
  }).catch(() => {
    refreshHudAndReport(true);
  });

  browser.runtime.onMessage.addListener((msg) => {
    if (!msg) return;
    if (msg.type === "studioProConfig" && msg.config) {
      const prevCollapse = cfg.autoCollapseSidebar;
      cfg = { ...cfg, ...msg.config };
      if (!prevCollapse && cfg.autoCollapseSidebar) {
        sidebarCollapsedOnce = false;
      }
      refreshHudAndReport(true);
    } else if (msg.type === "studioProAction") {
      switch (msg.action) {
        case "triggerUpload":
          triggerSmartStudioUpload();
          break;
        case "insertTemplate":
          insertDescriptionTemplate(msg.payload || "tutorial");
          break;
        case "generateTags":
          generateSmartTagsFromTitle();
          break;
        case "copyTags":
          copyCurrentVideoTags();
          break;
        case "downloadThumb":
          downloadHdThumbnail();
          break;
        case "collapseSidebar":
          sidebarCollapsedOnce = false;
          applyAutoCollapseSidebar();
          break;
        case "refreshMetrics":
          refreshHudAndReport(true);
          break;
      }
    }
  });

  setInterval(() => refreshHudAndReport(false), 2500);
})();
