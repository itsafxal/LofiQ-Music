# Update Installation Lifecycle & Infinite Loop Prevention

## 1. Multi-Stage Update State Machine

The update lifecycle strictly distinguishes each phase:

```
[ Update Check ] ──> (remoteVersionCode > installedVersionCode?)
       │
       ├── NO  ──> [ Report Up to Date ] (Dismiss notification & state)
       └── YES ──> [ Notify & Offer Update ]
                         │
                         ▼
                   [ Download APK ]
                         │
                         ▼
                   [ SHA-256 Checksum Verification ]
                         │
                         ├── MISMATCH ──> [ Delete File & Fail Fast ]
                         └── PASS ──> [ Inspect APK Package (pm.getPackageArchiveInfo) ]
                                            │
                                            ├── archivePkg != com.sargamify.app ──> [ Delete & Abort ]
                                            ├── archiveCode <= installedCode    ──> [ Delete & Abort (Infinite Loop Guard) ]
                                            └── archiveCode > installedCode     ──> [ Launch System PackageInstaller Intent ]
                                                                                           │
                                                                                           ▼
                                                                                   [ User Confirms in OS ]
                                                                                           │
                                                                                           ▼
                                                                                   [ App Restart / Resume ]
                                                                                           │
                                                                                           ▼
                                                                                   [ Re-evaluate Installed Version via PackageManager ]
```

---

## 2. Preventing the Infinite Update Loop
An infinite update loop occurs when an updater repeatedly prompts the user to install a version that has already been installed or cannot upgrade the app.

LofiQ eliminates this permanently through three safeguards:
1. **Dynamic Installed Identity**: `AppUpdateManager` compares against `AppVersionProvider.resolveVersion(context).versionCode`, never stale cached preferences.
2. **Pre-Install Archive Inspection**: If a downloaded APK binary is actually older than or equal to the currently installed app, `AppUpdateManager.verifyApkPackage` rejects the file, deletes it, and refuses to invoke `context.startActivity(installerIntent)`.
3. **Post-Resume Cache Invalidation**: Upon activity resumption, `MainScreen` and `SettingsViewModel` refresh the installed version. If the installed version meets or exceeds the remote version, all update notifications and modal prompts are cleared automatically.
