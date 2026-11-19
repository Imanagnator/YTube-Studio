# Contributing to YTube Studio

Thank you for your interest in contributing to **YTube Studio**!

## How to Contribute

1. **Fork** the repository (`https://github.com/Imanagnator/YTube-Studio`).
2. Create a feature or bugfix branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
3. Make your changes following the coding standards below.
4. Verify your build locally:
   ```bash
   gradle :app:assembleDebug
   ```
5. Open a **Pull Request** describing what you changed, why, and how you tested it.

## Coding Standards

- **Language**: Kotlin + Jetpack Compose (Material Design 3).
- **Architecture**: MVVM with `StateFlow` and Kotlin Coroutines.
- **Real Code Only**: Never submit mocks, placeholder implementations, or dead-end UI controls.
- **Privacy & Secrets**: Never commit API keys, `.env` files, personal tokens, or `.jks`/`.keystore` signing files.
- **Permissions**: Follow least-privilege Android permissions and edge-to-edge layout best practices.

## Licensing

By contributing to YTube Studio, you agree that your contributions will be licensed under the **GNU General Public License v3.0 (GPL-3.0)**, matching the project's [LICENSE](LICENSE).
