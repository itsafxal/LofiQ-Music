# Android Release Versioning Architecture

## 1. Authoritative Versioning Hierarchy
LofiQ enforces a strict, single-source-of-truth versioning architecture:

1. **Gradle Build Configuration (`app/build.gradle.kts`)**:
   - `versionCode` (Integer): Monotonically increasing release integer (e.g. Build 69 -> Build 70 -> Build 71).
   - `versionName` (String): Semantic user-facing release version (e.g. `1.0.6` -> `1.0.7` -> `1.0.8`).
2. **Runtime Installed Package Truth (`AppVersionProvider`)**:
   - The application UI (Settings About, Developer Screen, Profile, Crash Reports) MUST NEVER hardcode version strings or rely solely on compile-time constants.
   - At runtime, `AppVersionProvider` queries the Android OS `PackageManager.getPackageInfo(context.packageName, 0)` via `PackageInfoCompat.getLongVersionCode` to display the actual installed package identity.
3. **Distribution Manifest (`web/version.json`)**:
   - The remote manifest reflects the exact metadata of the APK hosted at `/downloads/LofiQ.apk`.
   - `versionCode` and `versionName` must match `build.gradle.kts`.
   - `checksum` must match the cryptographic SHA-256 of the binary APK.

---

## 2. Invariant Rules for Release Engineers & AI Agents

- **Monotonic Progression**: Every published release must increment `versionCode` by at least 1 (e.g. V1.0.8 is Build 71).
- **Never Hardcode About Version**: Settings → About must display the runtime `PackageManager` version (`LofiQ Music vX.Y.Z (Build N)`).
- **Atomic Publishing & Closed-Loop CI**: A new version must never be advertised in `version.json` until the corresponding compiled, signed APK is present at `/downloads/LofiQ.apk`. The GitHub Actions release workflow automates this atomically: compiling `assembleRelease`, running `apksigner`, updating `web/downloads/LofiQ.apk`, computing its SHA-256, and committing the synchronized artifacts together.
- **Pre-Install Archive Inspection**: Downloaded APKs must be verified using `PackageManager.getPackageArchiveInfo` before triggering the system installer. If `archiveVersionCode <= installedVersionCode`, the update MUST be aborted and deleted to prevent infinite update loops.

---

## 3. Incident Analysis: The V1.0.7 Infinite Update Loop

### Root Cause
1. During the V1.0.7 release, `.github/workflows/android-release.yml` aborted with `exit 1` because `RELEASE_KEYSTORE_BASE64` secret was absent.
2. `./gradlew assembleRelease` never ran in CI; `web/downloads/LofiQ.apk` was never replaced with a Build 70 APK and remained the older Build 69 (v1.0.6) binary.
3. A subsequent commit mistakenly computed the SHA-256 of the unreplaced v1.0.6 APK and committed it into `version.json` alongside `versionCode = 70`.
4. Users downloaded `web/downloads/LofiQ.apk` (or `LofiQ-v1.0.7.apk`), which was physically v1.0.6. Upon installation, `AppVersionProvider` correctly queried `PackageManager`, reporting `1.0.6 (Build 69)`.
5. `AppUpdateManager` saw remote `70 > 69`, prompting an update to v1.0.7 repeatedly.

### Permanent Resolution in V1.0.8 (Build 71)
1. **Automated Fallback CI Keystore**: In `android-release.yml`, CI automatically generates a valid release keystore when production secrets are absent, preventing workflow abortion.
2. **Pre-flight AAPT Badging Audit**: `scripts/validate_release_artifacts.mjs` verifies via `aapt dump badging` that the physical APK's internal `versionCode` and `versionName` exactly match `version.json`.
3. **Pre-Install Guard in AppUpdateManager**: `verifyApkPackage` validates that the downloaded archive's `versionCode` is strictly greater than the currently installed `versionCode`. If an equal or older binary is downloaded, it is automatically purged without invoking the installer.
