# YTube Studio — Project Continuity & Architecture Memory

> **PURPOSE**: Permanent hidden project memory stored inside `.project_memory/` (tracked in Git so it is never lost across sessions). Contains **ZERO secrets, keys, or tokens**. Every coding agent working on this repository MUST read this file and `FEATURE_LOCK.json` before modifying or removing any feature.

---

## 1. Project Identity
- **App Name**: YTube Studio
- **Author & Maintainer**: Tamal Mitra (`@Imanagnator`)
- **Repository**: `https://github.com/Imanagnator/YTube-Studio`
- **Package / Application ID**: `com.tamalmitra.ytubestudio`
- **Android Namespace**: `com.example` *(DO NOT rename `com/example/` package directories or `namespace`)*
- **License**: GNU GPL v3.0
- **Current Version**: `1.0.1` (`versionCode = 2`)
- **Workflow Preference**: Whenever a new version is built & verified, push `main` + the version tag (`vX.Y.Z`) directly to GitHub so GitHub Actions builds and publishes the signed Release APK for testing.

---

## 2. Non-Negotiable Architecture Invariants (NEVER BREAK)
1. **Feature Preservation**: Never remove, hide, or regress any feature listed in `FEATURE_LOCK.json` without explicit approval from Tamal Mitra.
2. **Release APK `yt-dlp` + `FFmpeg` Stability**:
   - `app/src/main/AndroidManifest.xml` MUST keep `android:extractNativeLibs="true"` on `<application>`.
   - `app/proguard-rules.pro` MUST keep `com.yausername.**`, `com.fasterxml.jackson.**` (+ annotations), `org.apache.commons.**`, `org.tukaani.**`, `com.example.data.model.**`, and `com.example.gecko.**`.
   - `MediaDownloadManager.kt` MUST keep the 4-stage multi-client extraction fallback (`android_vr,mweb,tv` → `cookies+mweb,tv,web_embedded` → `ios,android,web_embedded` → `default`), canonical URL normalization, `--no-playlist`, and self-healing `UpdateChannel._NIGHTLY` retry.
3. **Cold-Start & Post-Login Speed**:
   - Desktop YouTube Studio session (`studioSession`) MUST remain lazy-loaded on demand (never booted on app cold start).
   - GeckoView content blocking MUST remain balanced (`DEFAULT` + `ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS`) so Google login and YouTube cookies never stall.
   - Inactive browser tabs MUST suspend automatically while respecting Background Audio playback.
4. **Signing & Secrets Hygiene**:
   - Never commit `.jks`, `.keystore`, `.env`, Personal Access Tokens, or `local.properties` to Git.
   - Never modify `signingConfigs` or `buildTypes.*.signingConfig` in `app/build.gradle.kts`.
5. **Removed Features (Do NOT Re-Add Unless Requested)**:
   - Floating download overlay pill (replaced by hooking YouTube's built-in Download button).
   - Network speed meter module.
   - Any AI/Gemini placeholder branding or unused API keys.

---

## 3. Core Modules Map
- `app/src/main/java/com/example/MainActivity.kt` — Adaptive M3 scaffold (NavBar/NavRail), edge-to-edge fullscreen (`SENSOR_LANDSCAPE`), SAF upload picker, and tab orchestration.
- `app/src/main/java/com/example/gecko/GeckoEngineManager.kt` — Mozilla GeckoView runtime, Stream & lazy Studio sessions, WebExtension controller approving all 7 managed extension IDs, persistent extension hydration, and DNS-over-HTTPS TRR wiring.
- `app/src/main/java/com/example/gecko/ManagedExtensionUpdater.kt` — Catalog of all 7 verified official Mozilla-signed extensions + updater querying Mozilla AMO API v5 and `gorhill/uBlock` GitHub Releases.
- `app/src/main/assets/extensions/tubedeck_adblock/content.js` — Companion WebExtension: CDN preconnect hints, YouTube Download button hook, live quality inspection/switching, background playback protection, and **YTube Studio Creator Toolkit** floating bar on `studio.youtube.com` video edit pages.
- `app/src/main/java/com/example/downloader/MediaDownloadManager.kt` — `youtubedl-android` (`yt-dlp` Nightly + `FFmpeg`) multi-client extractor, progress streaming, MediaStore export (`Music/YTubeStudio`, `Movies/YTubeStudio`), and auto-update engine.
- `app/src/main/java/com/example/ui/TubeDeckViewModel.kt` — Central state coordinator, 24h background auto-updater for all 7 extensions + `yt-dlp` nightly, download queue, offline Media3 controller, DoH failover, and screen recorder state.
- `app/src/main/java/com/example/ui/components/ExtensionsHubCard.kt` — Full Mozilla Extensions Hub UI card in **Shield & App** (X/7 active counter, Update All button, global Auto-Update switch, Creator Toolkit banner, per-extension toggle & refresh).
- `app/src/main/java/com/example/ui/screens/` — `StreamScreen.kt`, `StudioScreen.kt`, `ProfileScreen.kt` (Downloads, Offline Media3 Player, Shield & App, DNS, Notifications, About).
- `app/src/main/java/com/example/network/` — Dynamic DoH latency monitor & failover engine.
- `app/src/main/java/com/example/service/` — `BackgroundPlaybackService`, `DownloadForegroundService`, `OfflineAudioService`, `ScreenRecordService`.

---

## 4. Release History & Roadmap
- **`v1.0.0`** — YTube Studio rebrand (`com.tamalmitra.ytubestudio`), uBlock Origin auto-updater, cold-start/login speed optimization, Media3 offline player, YouTube native Download button hook, fullscreen landscape + quality switcher, Dynamic Auto-DNS failover, Screen Recorder, GitHub Actions CI.
- **`v1.0.1`** — Full Mozilla Extensions Hub (7 official signed extensions), full 24h auto-updater for all extensions + `yt-dlp` nightly engine, built-in YTube Studio Creator Toolkit (SEO char counters, HD thumbnail opener, tag copier), Release APK `yt-dlp` R8 + `extractNativeLibs` + 4-stage multi-client fix, and permanent `.project_memory/` continuity database.
- **Upcoming Roadmap Ideas**:
  - Permanent GitHub Release keystore secret (`RELEASE_KEYSTORE_BASE64`) so every GitHub Release updates cleanly in-place with zero signature conflicts.
  - Optional in-app GitHub Release self-updater banner notifying when a newer `vX.Y.Z` tag is published on `Imanagnator/YTube-Studio`.
  - Custom `yt-dlp` download quality presets & audio-only playlist queue support.
