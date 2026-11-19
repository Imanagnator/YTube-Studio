# Changelog

All notable changes to **YTube Studio** by **Tamal Mitra** are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] - 2026-04-12

### Fixed
- **YouTube Studio Video / Thumbnail / Subtitle Uploads**
  - Sanitized raw file extensions (`.mp4`, `.mov`, `.mkv`, `.srt`, etc.) sent by YouTube Studio's `<input accept="...">` into valid Android MIME families with safe `*/*` fallback so Android's file picker never greys out or blocks selectable files.
  - Prevented `studioSession` from deactivating (`setActive(false)`) while the Android system file picker is open so Studio never drops upload callbacks.
  - Added `upload.youtube.com`, `googleapis.com`, and `clients.google.com` to Studio infrastructure hosts and wired the red **Upload** shortcut to directly trigger YouTube Studio's native Upload modal.
- **Extension Login & Signup Support (vidIQ & Installed Extensions)**
  - Allowed trusted domains for installed extensions (`vidiq.com`, `vid.io`, `ajay.app`, `returnyoutubedislike.com`, `mrfdev.com`, `moz-extension://`) to open inside a synced in-app Auth Modal sharing the same GeckoRuntime cookie jar, automatically reloading Studio on **Done** while keeping random external links safely blocked.

### Removed
- Removed the non-working Studio `%` screen zoom selector from the Studio top bar and disabled CSS `style.zoom` injection on `studio.youtube.com` (per user request, also improving Studio touch hitboxes).

## [1.0.2] - 2026-04-12

### Fixed
- **uBlock Origin & GeckoView WebExtensions Engine Reliability**
  - Enabled GeckoView's dedicated `extensionsProcessEnabled(true)` and `extensionsWebAPIEnabled(true)` runtime settings plus automatic `ExtensionProcessDelegate` crash respawn (`enableExtensionProcessSpawning()`).
  - Wired `webExtensionController.setTabActive(...)` across Stream & Studio tab switches so GeckoView's `mobileWindowTracker` always exposes the active tab to uBlock Origin, SponsorBlock, Enhancer for YouTube, and vidIQ.
  - Switched extension installation to Firefox for Android's direct Mozilla AMO HTTPS install path (`INSTALLATION_METHOD_MANAGER`) with local signed XPI (`INSTALLATION_METHOD_FROM_FILE`) fallback.
  - Made Mozilla AMO (`ublock-origin`) the primary source for uBlock Origin with `gorhill/uBlock` GitHub Releases as fallback, preventing mobile carrier IP GitHub HTTP 403 rate limits.
  - Added automatic first-boot stream reload once uBlock Origin activates and manual one-tap reinstall/repair via each extension's Refresh button.
  - Added a lightweight, CSP-safe companion YouTube Ad-Shield failsafe (cosmetic ad-slot hiding + instant pre-roll skip while uBlock initializes its filter lists) bridged to the live Ads Blocked counter.

### Security
- First release signed with Tamal Mitra's permanent 27-year GitHub Repository Secret keystore for seamless in-place updates from `v1.0.2` onward.

## [1.0.1] - 2026-04-12

### Added
- **Full Mozilla Extensions Hub (7 Official Signed Extensions)**
  - Added seamless installation, ON/OFF toggling, and semantic version tracking for **uBlock Origin**, **SponsorBlock**, **Return YouTube Dislike**, **DeArrow**, **YouTube NonStop**, **Enhancer for YouTube**, and **vidIQ Vision for YouTube Studio**.
  - Added **ManagedExtensionUpdater** querying Mozilla Add-ons API v5 (`addons.mozilla.org`) and `gorhill/uBlock` GitHub Releases.
- **Full Auto-Update for All Extensions + `yt-dlp` Nightly Engine**
  - Background 24-hour auto-updater now installs/updates all 7 enabled Mozilla extensions **and** keeps the `yt-dlp` extraction engine updated to the latest nightly build automatically (plus one-tap **Update All** and per-extension refresh in **Shield & App**).
- **Built-In YTube Studio Creator Toolkit**
  - Added an unobtrusive floating creator bar on `studio.youtube.com` video edit pages with live SEO Title (`100` chars) & Description (`5000` chars) counters, 1-tap HD Thumbnail opener (`maxresdefault`), and 1-tap Tag Copier.

### Fixed
- **Release APK `yt-dlp` + `FFmpeg` Extraction & Download Reliability**
  - Added `android:extractNativeLibs="true"` in `AndroidManifest.xml` so native Python/FFmpeg binaries extract properly on modern Android devices.
  - Hardened `proguard-rules.pro` with explicit keep rules for `youtubedl-android`, Jackson annotations, `org.apache.commons`, and `org.tukaani` so R8 minification never breaks `yt-dlp` metadata parsing in Release APKs.
  - Upgraded `MediaDownloadManager` with a 4-stage multi-client extraction fallback chain (`android_vr,mweb,tv` → `cookies + mweb,tv,web_embedded` → `ios,android,web_embedded` → `default`), canonical URL normalization, `--no-playlist`, and self-healing `yt-dlp` nightly retry.

## [1.0.0] - 2026-04-12

### Added
- **YTube Studio Rebrand & Production Release (`com.tamalmitra.ytubestudio`)**
  - Custom adaptive launcher icon, obsidian Material 3 theme, and full in-app About & open-source attribution card for Tamal Mitra.
  - Enabled R8 code minification, resource shrinking, and tailored ProGuard rules for GeckoView, `yt-dlp`, Room, and Media3.
  - Added GitHub Actions CI/CD release workflow using encrypted GitHub Secrets for APK signing.
- **Automatic Extension Updates (`gorhill/uBlock`)**
  - Added background 24-hour update checks against official `gorhill/uBlock` GitHub Releases with semantic version comparison and automatic signed `.xpi` installation.
  - Added toggleable **Auto-Update Extensions** setting in **Shield & App**.
- **Media & Downloads Hub (Tab 3)**
  - Integrated embedded AndroidX Media3 offline audio & video player with playback queue, shuffle, repeat, variable speed (`0.75x–2.0x`), seek controls, and sleep timer.
  - Hooked YouTube's built-in Download button directly into YTube Studio's `yt-dlp` + `FFmpeg` Download Manager.
- **Full Landscape Fullscreen & Live Quality Control**
  - Automatic `SENSOR_LANDSCAPE` fullscreen rotation with immersive system bars and Back-key exit.
  - Live YouTube stream resolution inspection and manual switching (`Auto` through `2160p`).
- **Dynamic Auto-DNS Failover**
  - Automated DoH latency monitoring with failover across System Default, Cloudflare, Google, Quad9, AdGuard, NextDNS, and custom DoH resolvers.

### Changed
- **Major Page-Load & Post-Login Speed Improvements**
  - Lazy-loaded Desktop Studio session on demand instead of booting alongside `m.youtube.com` on cold start.
  - Switched GeckoView content blocking from strict non-tracker cookie blocking to balanced first-party cookie isolation (`DEFAULT` + `ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS`), fixing post-login slowdowns and avoiding duplicate filtering alongside uBlock Origin.
  - Fixed Google login completion detection to validate the actual destination host rather than query strings, preventing premature reload loops.
  - Suspended inactive browser tabs automatically to free CPU and memory for the active tab.
  - Deferred initial DNS benchmarking and extension checks so cold-start YouTube streaming receives immediate bandwidth priority.
  - Added CDN `<link rel="preconnect">` hints and reduced DOM polling overhead in the companion WebExtension.
- Removed floating download overlay pill and legacy speed meter module to streamline the interface and reduce APK size.

### Fixed
- Added `FOREGROUND_SERVICE_DATA_SYNC` permission for Android 14–16 foreground download stability.
- Fixed race conditions in download cancellation, screen recording stop/save lifecycle, and file sharing URIs.
