# Changelog

All notable changes to **YTube Studio** by **Tamal Mitra** are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
