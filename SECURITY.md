# Security Policy

## Supported Versions

The following versions of **LofiQ** currently receive security updates and vulnerability patches:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0.0 | :x:                |

---

## Reporting a Vulnerability

We take the security of LofiQ, its users, and its contributors seriously. If you discover a security vulnerability, we appreciate your help in disclosing it to us responsibly.

**Please DO NOT open a public GitHub issue for security vulnerabilities.**

Instead, please report security vulnerabilities via:
- **Private GitHub Vulnerability Reporting:** Navigate to the repository's **Security** tab and click **Report a vulnerability**.
- **Email:** Contact the maintainers directly with details, steps to reproduce, and impact analysis.

### What to Include in Your Report
1. Description of the vulnerability and its potential impact.
2. Step-by-step instructions or proof-of-concept (PoC) to reproduce the issue.
3. Affected components, versions, and build variants.
4. Suggested remediation or patch, if available.

### Response Timeline
- **Initial Response:** Within 48 hours acknowledging receipt of your report.
- **Triage & Assessment:** Within 5 business days confirming severity and remediation timeline.
- **Release of Fix:** Critical issues will be patched promptly in an expedited release.

---

## Security Architecture & Rules

### 1. Embedded Client Architecture
LofiQ operates as a privacy-first, serverless Android client. It does not employ a proprietary backend server to intermediate music streaming or user telemetry. All music streams are resolved directly on-device using public client endpoints.

### 2. User Credentials & Sessions
- **Session Isolation:** YouTube authentication cookies and Spotify tokens are stored strictly in local device sandboxes (`PreferenceDataStore`).
- **No Cloud Upload of Sessions:** User web session cookies (`SAPISID`, `sp_dc`) are never uploaded to Cloud Firestore or external logging servers.
- **Backup Exclusion:** Sensitive DataStore preferences (`lofiq_auth_preferences`, `lofiq_spotify_preferences`) are explicitly excluded from Android Cloud Backups and device transfer via `data_extraction_rules.xml` and `backup_rules.xml`.

### 3. Firebase & Cloud Firestore
- **Access Control:** All Firestore collections enforce strict user authentication and authorization rules (`firestore.rules`). Users can only read and write documents matching their verified `request.auth.uid`.
- **Firebase App Check:** Integrated with Google Play Integrity in production builds to ensure that only genuine, untampered LofiQ instances can access Cloud Firestore.
- **Public API Keys:** Firebase Web API keys found in `google-services.json` are client identifiers used by Google Play Services to route requests to your Firebase project. They do not grant administrative database access. True database security is strictly enforced by server-side `firestore.rules`.

### 4. Third-Party Integrations
- **Last.fm:** API credentials must be configured locally in `local.properties` (`LASTFM_API_KEY`, `LASTFM_API_SECRET`) and are compiled via `BuildConfig`. Never commit private API secrets to source control.
- **Network Security:** Cleartext HTTP traffic is disabled globally via `network_security_config.xml`, with domain-level scoping permitted strictly for third-party lyric endpoints (`krcs.kugou.com`).
- **WebView Intent Hardening:** All WebView intent schemes are validated with `CATEGORY_BROWSABLE` and cleared components to prevent intent redirection and component hijacking attacks.

---

## Credential Rotation & History Cleanup Procedure

If you are open-sourcing or forking this repository and private credentials were previously committed in earlier commits:

### 1. Rotate Credentials Immediately
- **Last.fm API Key & Secret:** Log in to [Last.fm API Console](https://www.last.fm/api/account/create) and generate a new API key pair. Update `local.properties`.
- **Firebase Web API Key & SHA Fingerprints:** In the Firebase Console, restrict your Web API Key to the `com.sargamify.app` package name and production SHA-256 fingerprint, or create a fresh Firebase project for open-source distribution.

### 2. Purge Secrets from Git History
Removing a file or secret in a new commit does **NOT** remove it from prior Git history. Before making a repository public, sanitize git history using [git-filter-repo](https://github.com/newren/git-filter-repo) or [BFG Repo-Cleaner](https://rtyley.github.io/bfg-repo-cleaner/):

```bash
# Example using git-filter-repo to purge sensitive files from all commits:
git filter-repo --invert-paths --path app/google-services.json --path local.properties

# Purge specific text secrets from all commit history:
git filter-repo --replace-text <(echo "4a9f5581a9cdf20e699f540ba52a07a9==>REDACTED")
git filter-repo --replace-text <(echo "86ebfe38221fa0c2d3cf3832d2077e68==>REDACTED")
```

