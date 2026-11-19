# Smart Application Update System Architecture

## Overview
LofiQ Music provides a robust, dual-channel release discovery, integrity validation, and installation system supporting both **Google Play In-App Updates** and **Authorized Direct APK Distribution**.

---

## 1. Distribution Channel Detection

```mermaid
flowchart TD
    CheckUpdate([Check For Update]) --> QueryMetadata[Fetch version.json from Primary/Secondary Endpoint]
    QueryMetadata --> ValidateVersion{versionCode > Current?}
    ValidateVersion -- No --> UpToDate[No Action / Dismiss Notification]
    ValidateVersion -- Yes --> SourceCheck{Installed Via Google Play?}
    SourceCheck -- Yes (com.android.vending) --> PlayStoreFlow[Direct User to Play Store / In-App Updates]
    SourceCheck -- No (Direct APK / Sideload) --> DirectApkFlow[Direct APK Secure Pipeline]
    
    subgraph Direct APK Pipeline
        DirectApkFlow --> CheckCooldown{Notified within 24h?}
        CheckCooldown -- Yes --> SuppressNotify[Suppress Redundant Notification]
        CheckCooldown -- No --> PostNotification[Show Deduplicated Notification with Later Action]
        PostNotification --> UserAction{User Action}
        UserAction -- "Later" --> RecordDeferral[Record 24h Deferral Timestamp]
        UserAction -- "Update Now" --> DownloadApk[Download APK with Progress]
        DownloadApk --> VerifyChecksum{SHA-256 Checksum Match?}
        VerifyChecksum -- Failed --> CorruptError[Reject Installation & Delete Corrupted File]
        VerifyChecksum -- Passed --> VerifyPackage{Package & Signature Match?}
        VerifyPackage -- Passed --> WarnPlayback{Music Playing?}
        WarnPlayback -- Yes --> ShowPlaybackWarning[Display Interruption Warning]
        WarnPlayback -- No --> LaunchInstaller[Launch System Intent ACTION_VIEW FileProvider]
        ShowPlaybackWarning --> LaunchInstaller
    end
```

---

## 2. Release Discovery & Validation

### Dual Endpoint Redundancy
1. **Primary Endpoint**: Vercel (`https://lofiq-web.vercel.app/version.json`)
2. **Operational Secondary**: Netlify (`https://mybackuplofiq.netlify.app/version.json`)
- Strict qualification rules: Fallback occurs only on DNS failure, connection timeout, transport TLS failure, or HTTP 5xx errors. Client errors (4xx) or malformed JSON fail immediately without exposing fallback endpoints.

### Integrity Verification Pipeline
1. **SHA-256 Digest Verification**: Prior to invoking the system package installer, the downloaded APK's SHA-256 hash is computed in streaming chunks and matched against `expectedChecksum`. Mismatched files are discarded immediately.
2. **Package Identity Check**: Uses Android `PackageManager.getPackageArchiveInfo()` to verify that the downloaded package matches `com.sargamify.app` before passing it to `FileProvider`.
3. **Minimum SDK Compatibility**: Validates `minSdkVersion` against device `Build.VERSION.SDK_INT`.

---

## 3. Notification Deduplication & User Control
- **Dedicated Notification Channel**: `lofiq_app_updates` with high priority.
- **24-Hour Cooldown**: Notifications for the exact same version are throttled to a minimum of 24 hours.
- **Action Buttons**:
  - **Update Now**: Launches `MainActivity` with download flags.
  - **Later**: Broadcasts to `UpdateNotificationReceiver`, saving the dismissal timestamp and closing the notification without badgering the user.
- **Android 13+ Support**: Explicitly guards against missing `POST_NOTIFICATIONS` permission.

---

## 4. In-App User Interface (`MainScreen.kt`)
- Displays:
  - Installed version vs New version (`v1.0.6` → `v1.0.7`).
  - Download size.
  - Formatted release notes.
  - Live downloading progress bar with percentage.
  - Active playback notice: *"Active music playback will pause when installing this update."*
- Channel-aware button: Shows **"Update on Play Store"** for Google Play users, or **"Update Now"** with direct download for sideloaded users.
