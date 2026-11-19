# Security Policy

## Supported Versions

| Version | Supported |
| :--- | :--- |
| `1.0.x` | Yes |

## Secret & Signing Key Handling

YTube Studio follows a strict **zero-secrets-in-repository** policy:

- No signing keystores (`.jks`, `.keystore`), passwords, API keys, or personal access tokens are ever committed to source control.
- Production releases are signed exclusively via local environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) or encrypted GitHub Actions Repository Secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`).
- Built-in uBlock Origin updates only install Mozilla-signed `.xpi` packages fetched over HTTPS directly from the official `gorhill/uBlock` GitHub Releases API.

## Reporting a Vulnerability

If you discover a security vulnerability in YTube Studio:

1. **Do not** open a public GitHub issue containing exploit details or sensitive credentials.
2. Use GitHub's private **Security Advisories** tab on `https://github.com/Imanagnator/YTube-Studio/security/advisories` to submit a confidential report.
3. Include steps to reproduce, affected Android version, and any suggested remediation.

Reports are reviewed by the maintainer (**Tamal Mitra**) as promptly as possible.
