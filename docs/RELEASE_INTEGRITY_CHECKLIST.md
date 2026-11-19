# Release Integrity & Pre-Publication Checklist

Before tagging or deploying any new release of LofiQ Music, verify every item below:

---

## 1. Version Numbering Alignment
- [x] `versionCode` in `app/build.gradle.kts` is strictly greater than the highest previously published release.
- [x] `versionName` in `app/build.gradle.kts` matches the release tag (e.g. `1.0.7` matches `v1.0.7`).
- [x] `versionCode` and `versionName` in `web/version.json` match `app/build.gradle.kts` exactly.

---

## 2. Package & Archive Verification
- [x] `web/downloads/LofiQ.apk` exists, has size > 15 MB, and matches the SHA-256 in `web/version.json`.
- [x] `scripts/validate_release_artifacts.mjs` passes with zero errors.
- [x] Downloaded APKs are inspected with `PackageManager.getPackageArchiveInfo` before installation.
- [x] Stale APKs with `archiveVersionCode <= installedVersionCode` are rejected and deleted.

---

## 3. UI Version Transparency
- [x] Settings → About queries runtime `PackageManager` via `AppVersionProvider`.
- [x] Developer Screen and Profile Screen reflect runtime package info.
- [x] No hardcoded version text exists in user-facing About elements.
- [x] Update notifications are suppressed when the application is up to date.
