# YTube Studio

<p align="center">
  <strong>Ad-Free YouTube Streaming, Desktop Creator Studio & Offline Media Hub for Android</strong>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-1.0.0-E50914?style=flat-square" />
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%2010%2B%20(arm64)-00E676?style=flat-square" />
  <img alt="License" src="https://img.shields.io/badge/license-GPL--3.0-00E5FF?style=flat-square" />
  <img alt="Author" src="https://img.shields.io/badge/author-Tamal%20Mitra-FFB300?style=flat-square" />
</p>

---

## Overview

**YTube Studio** (`com.tamalmitra.ytubestudio`) is an open-source, privacy-focused Android client built with **Kotlin**, **Jetpack Compose**, and **Mozilla GeckoView**. It unifies ad-free YouTube streaming, full desktop YouTube Studio creator controls, a built-in `yt-dlp` + `FFmpeg` offline download manager, a dedicated Media3 offline audio/video player, dynamic DNS-over-HTTPS (DoH) failover, and automatic GitHub extension updates in a single Material 3 app.

---

## Key Features

- **Ad-Free Streaming with Auto-Updating uBlock Origin**
  - Runs the official Mozilla-signed **uBlock Origin** WebExtension natively inside GeckoView.
  - Automatically checks **`gorhill/uBlock`** GitHub Releases every 24 hours in the background and installs newer signed `.xpi` releases seamlessly (plus one-tap manual updates).
- **Fast Cold-Start & Post-Login Performance**
  - Lazy-loads Desktop Studio only when you open the Studio tab, cutting startup load time and RAM usage in half.
  - Balanced GeckoView content-blocking cookie isolation eliminates post-login cookie stalls and login redirect loops.
  - Inactive tabs suspend rendering automatically while preserving background audio when enabled.
- **Full Landscape Video & Real Resolution Control**
  - Automatic `SENSOR_LANDSCAPE` immersive fullscreen mode with edge-to-edge system bar hiding and Back-key exit.
  - Live YouTube player quality inspection and switching (`Auto`, `2160p`, `1440p`, `1080p`, `720p`, `480p`, `360p`, `240p`, `144p`) with actual resolved playback height feedback.
- **Desktop YouTube Studio Creator Workspace**
  - Dedicated desktop Firefox User-Agent session locked to `studio.youtube.com` with quick section shortcuts (Dashboard, Upload, Content, Analytics, Comments, Subtitles, Earn), zoom controls (`50%–130%`), native SAF file picker for uploads, and keep-screen-awake support.
- **Built-In Download Manager & Offline Media Player**
  - Hooks directly into YouTube's built-in Download button so tapping Download inside any video launches YTube Studio's format picker (`MP3 320kbps`, `M4A`, `MP4 1080p/720p/480p/360p`).
  - Powered by `youtubedl-android` (`yt-dlp` + `FFmpeg`) with MediaStore integration (`Music/YTubeStudio` and `Movies/YTubeStudio`).
  - Embedded **AndroidX Media3** offline audio/video player with queue navigation, shuffle, repeat, variable playback speed (`0.75x–2.0x`), seek controls, and sleep timer.
- **Dynamic Auto-DNS Failover (DoH)**
  - Monitors connectivity and DNS latency across System Default, Cloudflare, Google, Quad9, AdGuard DNS, NextDNS, and custom DoH servers, switching automatically when your active resolver slows down or fails.
- **Built-In Screen Recorder**
  - Captures `720p` or `1080p` MP4 recordings with optional microphone audio directly to `Movies/YTubeStudio`.

---

## Architecture & Tech Stack

| Layer | Technology |
| :--- | :--- |
| **UI & Design** | Kotlin, Jetpack Compose, Material Design 3 (Edge-to-Edge, Adaptive NavBar / NavRail) |
| **Browser Engine** | Mozilla GeckoView (`arm64-v8a`) + WebExtensions API (`uBlock Origin` + companion enhancer) |
| **Media Extraction** | `youtubedl-android` (`yt-dlp` + `FFmpeg`) |
| **Offline Playback** | AndroidX Media3 (`ExoPlayer`, `MediaSessionService`, `MediaController`) |
| **Local Storage** | Room Database (download queue & notifications), Jetpack DataStore (preferences) |
| **Networking** | OkHttp 4, Dynamic DNS-over-HTTPS (TRR) Engine |
| **Build & Release** | Gradle Kotlin DSL, R8 Code Minification & Resource Shrinking |

### Project Structure

```text
YTube-Studio/
├── app/
│   ├── build.gradle.kts                 # App module build, R8 release config & signing env hooks
│   ├── proguard-rules.pro               # Production R8 rules for GeckoView, yt-dlp, Room, Media3
│   └── src/main/
│       ├── AndroidManifest.xml          # Permissions, foreground services & activity config
│       ├── assets/extensions/           # uBlock Origin XPI + YTube Studio companion WebExtension
│       ├── java/com/example/
│       │   ├── MainActivity.kt          # Adaptive M3 scaffold, fullscreen & SAF pickers
│       │   ├── data/                    # Room DB, DAOs, DataStore preferences & models
│       │   ├── downloader/              # yt-dlp + FFmpeg MediaDownloadManager
│       │   ├── gecko/                   # GeckoEngineManager & UBlockOriginUpdater
│       │   ├── network/                 # DoH benchmarking & DynamicDnsMonitor
│       │   ├── notifications/           # Notification channels & persistent log
│       │   ├── service/                 # BackgroundPlayback, DownloadFG, OfflineAudio, ScreenRecord
│       │   └── ui/                      # ViewModel, Screens (Stream, Studio, Profile) & Components
│       └── res/                         # Adaptive launcher icons, strings & drawables
├── .github/
│   ├── workflows/android-release.yml    # Automated CI build & secret-signed GitHub Releases
│   └── ISSUE_TEMPLATE/                  # Bug report & feature request templates
├── CHANGELOG.md
├── CONTRIBUTING.md
├── LICENSE                              # GNU GPL v3.0
├── README.md
└── SECURITY.md
```

---

## Building From Source

### Prerequisites

- **JDK 21**
- **Android SDK** (Compile SDK 37, Target SDK 36, Min SDK 29)
- **Gradle 9.3.1+**

### Debug Build

```bash
gradle :app:assembleDebug
```

### Signed Production Release APK (R8 Minified)

Signing keys and passwords are **never stored in this repository**. Provide them exclusively through environment variables or CI secrets:

```bash
export KEYSTORE_PATH="/absolute/path/to/your-release-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"

gradle :app:assembleRelease
```

Output APK:
`app/build/outputs/apk/release/app-release.apk`

### Automated GitHub Releases (Hidden Secrets)

To publish signed production APKs automatically whenever you push a version tag (e.g. `v1.0.0`):

1. Open your GitHub repository → **Settings → Secrets and variables → Actions**.
2. Add the following **Repository Secrets** (encrypted and invisible to the public):
   - `RELEASE_KEYSTORE_BASE64`: Base64-encoded contents of your `.jks` keystore (`base64 -w 0 your-release-key.jks`)
   - `RELEASE_STORE_PASSWORD`: Your keystore store password
   - `RELEASE_KEY_PASSWORD`: Your key password (key alias must be `upload`)
3. Push a tag matching `v*` (e.g., `git tag v1.0.0 && git push origin v1.0.0`). GitHub Actions will compile, minify, sign, and attach the production APK to the Release automatically.

---

## Privacy & Security

- Zero third-party analytics, ads, telemetry SDKs, or external API keys.
- All signing keystores (`.jks`, `.keystore`), `.env` files, and build outputs are strictly excluded via `.gitignore`.
- See [SECURITY.md](SECURITY.md) for responsible disclosure guidelines.

---

## Disclaimer

YTube Studio is an independent open-source project and is **not affiliated with, endorsed by, or sponsored by YouTube or Google LLC**. All trademarks belong to their respective owners. Please respect content creators and applicable copyright laws when downloading media for personal offline use.

---

## Author & Credits

- **Author & Maintainer**: **Tamal Mitra** ([@Imanagnator](https://github.com/Imanagnator))
- **Upstream Open-Source Projects**:
  - [Mozilla GeckoView](https://mozilla.github.io/geckoview/)
  - [uBlock Origin](https://github.com/gorhill/uBlock) by Raymond Hill (`gorhill`)
  - [youtubedl-android](https://github.com/yausername/youtubedl-android) & [yt-dlp](https://github.com/yt-dlp/yt-dlp)
  - [AndroidX Media3](https://developer.android.com/media/media3)

---

## License

Copyright © 2026 **Tamal Mitra**.  
This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)** — see the [LICENSE](LICENSE) file for full details.
