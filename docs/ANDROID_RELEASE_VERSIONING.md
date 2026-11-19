# Android Release Versioning Architecture

## 1. Authoritative Versioning Hierarchy
LofiQ enforces a strict, single-source-of-truth versioning architecture:

1. **Gradle Build Configuration (`app/build.gradle.kts`)**:
   - `versionCode` (Integer): Monotonically increasing release integer (e.g. Build 69 -> Build 70).
   - `versionName` (String): Semantic user-facing release version (e.g. `1.0.6` -> `1.0.7`).
2. **Runtime Installed Package Truth (`AppVersionProvider`)**:
   - The application UI (Settings About, Developer Screen, Profile, Crash Reports) MUST NEVER hardcode version strings or rely solely on compile-time constants.
   - At runtime, `AppVersionProvider` queries the Android OS `PackageManager.getPackageInfo(context.packageName, 0)` via `PackageInfoCompat.getLongVersionCode` to display the actual installed package identity.
3. **Distribution Manifest (`web/version.json`)**:
   - The remote manifest reflects the exact metadata of the APK hosted at `/downloads/LofiQ.apk`.
   - `versionCode` and `versionName` must match `build.gradle.kts`.
   - `checksum` must match the cryptographic SHA-256 of the binary APK.

---

## 2. Invariant Rules for Release Engineers & AI Agents

- **Monotonic Progression**: Every published release must increment `versionCode` by at least 1.
- **Never Hardcode About Version**: Settings → About must display the runtime `PackageManager` version (`LofiQ Music vX.Y.Z (Build N)`).
- **Atomic Publishing**: A new version must never be advertised in `version.json` until the corresponding compiled, signed APK is present at `/downloads/LofiQ.apk`.
- **Pre-Install Archive Inspection**: Downloaded APKs must be verified using `PackageManager.getPackageArchiveInfo` before triggering the system installer. If `archiveVersionCode <= installedVersionCode`, the update MUST be aborted and deleted to prevent infinite update loops.
