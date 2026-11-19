let studioProConfig = {
  enabled: true,
  autoCollapseSidebar: true,
  focusMode: false,
  seoHudEnabled: true,
  privacyBlur: false,
  customTemplate: ""
};

let port = null;

function postNative(payload) {
  if (!port) return;
  try {
    port.postMessage(payload);
  } catch (_) {}
}

function sendToStudioTabs(payload) {
  if (!browser.tabs) return;
  browser.tabs.query({ url: "*://studio.youtube.com/*" }).then((tabs) => {
    for (const tab of tabs) {
      browser.tabs.sendMessage(tab.id, payload).catch(() => {});
    }
  }).catch(() => {});
}

function connectNative() {
  try {
    port = browser.runtime.connectNative("ytube_studio_pro");
    port.onMessage.addListener((msg) => {
      if (!msg) return;
      if (msg.type === "updateStudioProConfig") {
        studioProConfig = {
          enabled: typeof msg.enabled === "boolean" ? msg.enabled : studioProConfig.enabled,
          autoCollapseSidebar: typeof msg.autoCollapseSidebar === "boolean" ? msg.autoCollapseSidebar : studioProConfig.autoCollapseSidebar,
          focusMode: typeof msg.focusMode === "boolean" ? msg.focusMode : studioProConfig.focusMode,
          seoHudEnabled: typeof msg.seoHudEnabled === "boolean" ? msg.seoHudEnabled : studioProConfig.seoHudEnabled,
          privacyBlur: typeof msg.privacyBlur === "boolean" ? msg.privacyBlur : studioProConfig.privacyBlur,
          customTemplate: typeof msg.customTemplate === "string" ? msg.customTemplate : studioProConfig.customTemplate
        };
        sendToStudioTabs({ type: "studioProConfig", config: studioProConfig });
      } else if (msg.type === "studioProAction") {
        sendToStudioTabs({
          type: "studioProAction",
          action: msg.action || "",
          payload: msg.payload || ""
        });
      }
    });
    port.onDisconnect.addListener(() => {
      port = null;
    });
  } catch (_) {
    port = null;
  }
}

if (browser.runtime && browser.runtime.onMessage) {
  browser.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (!msg) return false;
    if (msg.type === "getStudioProConfig") {
      sendResponse(studioProConfig);
      return true;
    }
    if (msg.type === "studioProMetrics") {
      postNative(msg);
      return false;
    }
    if (msg.type === "studioProToast") {
      postNative(msg);
      return false;
    }
    return false;
  });
}

connectNative();
