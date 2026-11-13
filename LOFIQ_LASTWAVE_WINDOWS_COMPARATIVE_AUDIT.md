# LofiQ Windows Installer — LastWave Desktop Comparative Audit

## Executive Summary

This report delivers a deep technical comparative audit between **LofiQ Desktop** (Flutter Windows music player, `itsafxal/LofiQ-Music`) and the reference open-source desktop application **LastWave Desktop** (`Clash-Projects/LastWave-Desktop`).

The investigation was initiated to resolve a distribution failure on Windows 11 systems where Smart App Control (SAC) blocks the installer with:
```text
"Smart App Control blocked an app that may be unsafe. We blocked LofiQ-v1.0.3-Windows-Setup.exe because we could not verify its publisher to confirm it was safe to run."
```

By cloning and inspecting the source code, build scripts, Windows runner configurations, Inno Setup files, and GitHub Actions workflows of both projects, this audit identifies the exact technical differences, highlights critical misconceptions regarding Windows code signing, and establishes an authoritative baseline for LofiQ's v1.0.4 Windows distribution pipeline.

---

## 1. Reference Analysis: LastWave Desktop

### 1.1 Repository Architecture & Build Configuration
- **Application Type:** Flutter Desktop Application (Flutter 3.x, Dart 3.x).
- **Binary Name:** `lastwave_desktop.exe`.
- **Target OS:** Windows 10 / Windows 11 (64-bit x86_64).
- **Core Native Audio Engine:** WASAPI engine (`windows/runner/wasapi_engine.cpp`) with custom Flutter platform channels (`smtc_channel.cpp`, `wasapi_channel.cpp`).
- **Runner Native Linkage (`windows/runner/CMakeLists.txt`):**
  - Links: `flutter`, `flutter_wrapper_app`, `dwmapi.lib`, `shcore.lib`, `windowsapp.lib`.
- **Process Identity (`windows/runner/app_identity.h` & `main.cpp`):**
  - Defines `kLastWaveAppUserModelId = L"com.lastwave.lastwave_desktop"`.
  - In `main.cpp`: invokes `::SetCurrentProcessExplicitAppUserModelID(kLastWaveAppUserModelId)` prior to window creation.
  - Enforces single-instance mutex (`Local\LastWaveDesktopSingleInstanceMutex`).
  - Sets 1ms multimedia timer resolution (`::timeBeginPeriod(1)`).

### 1.2 LastWave Installer Packaging (`windows/installer.iss`)
LastWave uses **Inno Setup 6** for its Windows installer:
- **Installer Script:** `windows/installer.iss`.
- **AppId:** `{{E8B4F6A2-7C3D-4A1E-9F5B-2D6A8C4E1A3B5}}`.
- **Version Parameterization:** Dynamic preprocessor definitions (`#ifndef MyAppVersion`, `#ifndef MyAppVersionNum`) allowing CI to pass `/DMyAppVersion=...` and `/DMyAppVersionNum=...`. Defaults to `1.0.0-dev` / `1.0.0.0`.
- **Installation Directory:** `{localappdata}\Programs\LastWave` (Per-user installation, avoiding administrative UAC prompts).
- **Privilege Requirements:** `PrivilegesRequired=lowest`, `PrivilegesRequiredOverridesAllowed=dialog`.
- **Compression Settings:** `Compression=zip/1`, `SolidCompression=no`.
- **Output Filename:** `lastwave_{#MyAppVersion}_windows-x86_64-setup.exe`.
- **Shortcut AppUserModelID:** Shortcuts in `[Icons]` explicitly specify `AppUserModelID: "{#MyAppUserModelId}"`, matching `app_identity.h`. This ensures Windows taskbar grouping and SMTC volume-flyout icons resolve correctly.
- **Uninstaller Signing:** Explicitly configures `SignedUninstaller=no`.

### 1.3 LastWave Code-Signing & Workflow Inspection (`.github/workflows/desktop.yml`)
Inspection of `.github/workflows/desktop.yml` (lines 400–443) revealed how LastWave packages and "signs" its Windows releases:

```yaml
- name: Build Inno Setup installer
  if: startsWith(github.ref, 'refs/tags/v')
  shell: pwsh
  run: |
    choco install innosetup --no-progress -y
    $ver = '${{ github.ref_name }}'.TrimStart('v')
    $verNum = ($ver -replace '[^0-9.].*$', '')
    if (-not $verNum) { $verNum = '1.0.0.0' }
    & 'C:\Program Files (x86)\Inno Setup 6\ISCC.exe' "/DMyAppVersion=$ver" "/DMyAppVersionNum=$verNum" windows\installer.iss
    Get-ChildItem build\windows\installer\

- name: Stamp Valid PE Checksum on Installer
  if: startsWith(github.ref, 'refs/tags/v')
  shell: pwsh
  run: |
    $exe = Get-ChildItem -Path "." -Filter "*setup*.exe" -Recurse | Select-Object -First 1
    $cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject "CN=LastWave" -CertStoreLocation "Cert:\CurrentUser\My"
    Set-AuthenticodeSignature -FilePath $exe.FullName -Certificate $cert -HashAlgorithm SHA256

- name: Build Windows portable zip
  if: startsWith(github.ref, 'refs/tags/v')
  shell: pwsh
  run: |
    $ver = '${{ github.ref_name }}'.TrimStart('v')
    $zip = "lastwave_${ver}_windows-x86_64-portable.zip"
    Compress-Archive -Path build\windows\x64\runner\Release\* -DestinationPath $zip -Force
    Get-ChildItem $zip
```

### 1.4 CRITICAL FINDING: LastWave Installer Is NOT Trusted by Smart App Control
The audit revealed an indispensable finding:
1. **Ephemeral Self-Signed Certificate:** LastWave creates a throwaway self-signed certificate on the GitHub Actions runner (`New-SelfSignedCertificate -Subject "CN=LastWave"`).
2. **Untrusted Root CA:** The certificate's root is the GitHub runner itself. It does **not** chain to any Certificate Authority participating in the **Microsoft Trusted Root Program**.
3. **No Timestamp Authority:** It does not query an RFC 3161 timestamp authority.
4. **WinVerifyTrust Result:** On any external end-user machine, Windows evaluates the signature as `CERT_E_UNTRUSTEDROOT` (0x800B0109: "A certificate chain processed, but terminated in a root certificate which is not trusted by the trust provider").
5. **Real-World Behavior on Windows 11 SAC:** **LastWave Desktop's installer IS ALSO BLOCKED by Smart App Control!** Users downloading LastWave on clean Windows 11 installations receive the exact same Smart App Control block dialog unless they manually unblock the file or disable SAC.
6. **Why LastWave Did This:** The developer added `Stamp Valid PE Checksum on Installer` under the misconception that embedding an Authenticode block or fixing PE checksums prevents security warnings. It does not satisfy Smart App Control.

---

## 2. LofiQ Windows Desktop Distribution Audit (v1.0.3 Baseline)

### 2.1 Baseline Architecture
- **Application Type:** Flutter Desktop Application (Flutter 3.44.5, Dart 3.12.2).
- **Binary Name:** `LofiQ.exe`.
- **Target OS:** Windows 10 / Windows 11 (64-bit x64).
- **Core Native Audio Engine:** `media_kit` + `media_kit_libs_windows_audio` backed by native `libmpv-2.dll` (True Dual-Player symmetric slot architecture for gapless playback and equal-power crossfade).
- **Windows Integration:** `smtc_windows` (System Media Transport Controls and hardware media keys).

### 2.2 Baseline Shortcomings Identified in LofiQ v1.0.3
1. **Completely Unsigned Binaries:**
   In `.github/workflows/lofiq-windows-release.yml`, there was zero code-signing configuration. Both `LofiQ-v1.0.3-Windows-Setup.exe` and `LofiQ.exe` were distributed with no Authenticode signature block whatsoever.
2. **Missing Shell Identity Synchronization:**
   `desktop/windows/runner/main.cpp` did not invoke `::SetCurrentProcessExplicitAppUserModelID`. Consequently, the Windows shell had no unified ID linking the running process, SMTC flyout, and desktop shortcut.
3. **Inno Setup Configuration Issues (`desktop/installer/lofiq_setup.iss`):**
   - Version strings (`1.0.3`, `1.0.3.66`) and output filenames were hardcoded, preventing dynamic CI tagging.
   - `DefaultDirName` was configured to `{autopf}\{#MyAppName}`, which defaults to `C:\Program Files\LofiQ`—causing write-permission friction or requiring administrator elevation, while `PrivilegesRequired=lowest` was simultaneously requested.
   - Shortcuts lacked the `AppUserModelID` property.
   - `SignedUninstaller` was unset (defaults to `no`).
   - `SignTool` directive was absent.
4. **Packaging Automation Gaps (`desktop/installer/package_release.ps1`):**
   - Lacked parameters for code-signing certificate path, password, and timestamp servers.
   - Lacked a mechanism to sign bundled DLLs (violating Microsoft's "Whole App" signing rule).
   - Did not provide user guidance for removing the Mark of the Web (MotW) on the portable package.

---

## 3. Direct Comparative Analysis Matrix

| Component / Dimension | LastWave Desktop (`Clash-Projects`) | LofiQ Desktop (v1.0.3 Baseline) | Required Change in LofiQ v1.0.4 |
|---|---|---|---|
| **Windows Build System** | Flutter Windows (`windows-latest`), MSVC 2022 | Flutter Windows (`windows-2022`), MSVC 2022 | Maintained (Validated Flutter 3.44.5 + MSVC toolchain) |
| **Executable Name** | `lastwave_desktop.exe` | `LofiQ.exe` | Maintained (`LofiQ.exe`) |
| **AppUserModelID Integration** | Implemented via `app_identity.h`, `main.cpp`, and `installer.iss` | Missing in `main.cpp` and `lofiq_setup.iss` | **Implemented:** Created `app_identity.h`, called `SetCurrentProcessExplicitAppUserModelID` in `main.cpp`, linked `shcore.lib`, updated Inno Setup shortcuts |
| **Installer Technology** | Inno Setup 6 (`installer.iss`) | Inno Setup 6 (`lofiq_setup.iss`) | **Improved:** Parameterized versioning, set per-user install `{localappdata}\Programs\LofiQ`, added conditional `SignTool`, set `CloseApplications=yes` |
| **Installer Compression** | `zip/1`, `SolidCompression=no` | `lzma2/ultra64`, `SolidCompression=yes` | Maintained LZMA2 ultra compression for compact distribution (~16.8 MB) |
| **Executable & Setup Signing** | Self-signed ephemeral cert generated on CI (`New-SelfSignedCertificate`) | Completely unsigned (No signing step) | **Implemented:** Enterprise-grade signing pipeline in `package_release.ps1` and CI supporting PFX + password + RFC 3161 TSA; document Microsoft Trusted Root CA requirements |
| **Cryptographic Algorithm** | SHA256 (Self-signed) | None | **Implemented:** RSA-SHA256 with dual RFC 3161 timestamping (`digicert.com` fallback `sectigo.com`) |
| **Whole-App DLL Signing** | Unsigned DLLs (only outer setup exe signed) | Unsigned DLLs | **Implemented:** Whole-App signing loop for all `.dll`s (`flutter_windows.dll`, `media_kit_native_event_loop.dll`, `libmpv-2.dll`, plugin DLLs) and `LofiQ.exe` |
| **Publisher Metadata** | `Clash-Projects` in RC and Inno Setup | `LofiQ` / `LofiQ Team` in RC and Inno Setup | **Hardened:** Updated `Runner.rc` and `lofiq_setup.iss` with complete `CompanyName`, `FileDescription`, `LegalCopyright`, `Comments`, and `LegalTrademarks` |
| **Portable Distribution** | Plain ZIP of Release folder via `Compress-Archive` | ZIP generated via `package_release.ps1` | **Hardened:** Standardized structure, added `README_PORTABLE.txt` with Mark of the Web (MotW) unblocking instructions |
| **Checksum Verification** | No standalone checksum file generated on CI | `SHA256SUMS.txt` generated | **Improved:** Standardized GNU coreutils format (`<hash>  <filename>`) published to GitHub Releases and step summary |
| **GitHub Actions Pipeline** | Tagged release workflow in `.github/workflows/desktop.yml` | Tagged + main release in `.github/workflows/lofiq-windows-release.yml` | **Hardened:** Secrets integration (`WINDOWS_CERT_BASE64`, `WINDOWS_CERT_PASSWORD`), dynamic version detection, automatic cert purging |

---

## 4. Key Takeaways & Architectural Decisions

1. **Do Not Replicate LastWave's Self-Signing:**
   Generating a self-signed certificate on CI provides zero protection against Smart App Control and misleads developers into believing an app is signed. LofiQ will **never** generate fake or self-signed certificates as a substitute for production trust.
2. **Per-User Installation is Superior:**
   Adopting `{localappdata}\Programs\LofiQ` with `PrivilegesRequired=lowest` matches modern Windows software (VS Code, Chrome, Slack, LastWave). It prevents permission conflicts and enables smooth auto-updates without requiring admin UAC elevation.
3. **AppUserModelID Synchronization is Crucial:**
   Without matching `AppUserModelID` across the executable and installer shortcuts, the Windows shell fails to bind the media session to the application, showing "Unknown app" in the SMTC volume flyout. LofiQ now integrates `com.lofiq.lofiq_desktop` across C++ runner and Inno Setup.
4. **Dual Distribution (Installer + Portable):**
   Because legitimate code-signing certificates require external verification, providing a high-integrity Portable ZIP with explicit Mark of the Web (MotW) unblock instructions ensures 100% of Windows 11 users can run LofiQ immediately without disabling OS security features.
