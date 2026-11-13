# =====================================================================
# LofiQ Windows Desktop Release Packaging & Code-Signing Pipeline
# Version: 1.0.4 (Build 67)
# =====================================================================

param (
    [string]$Version = "",
    [string]$BuildNumber = "",
    [switch]$SkipBuild = $false,
    [string]$CertPath = "",
    [string]$CertPassword = "",
    [string]$TimestampServer = "http://timestamp.digicert.com",
    [switch]$SignBinaries = $false
)

$ErrorActionPreference = "Stop"

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "   LOFIQ WINDOWS DESKTOP PRODUCTION RELEASE PIPELINE   " -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan

$DesktopDir = Split-Path -Parent $PSScriptRoot
Set-Location $DesktopDir

# Extract version from pubspec.yaml if not provided
if (-not $Version) {
    $pubspecPath = Join-Path $DesktopDir "pubspec.yaml"
    if (Test-Path $pubspecPath) {
        $pubspecContent = Get-Content $pubspecPath -Raw
        if ($pubspecContent -match "version:\s*([0-9\.]+)\+?(\d*)") {
            $Version = $Matches[1]
            if (-not $BuildNumber -and $Matches[2]) { $BuildNumber = $Matches[2] }
        }
    }
}

if (-not $Version) { $Version = "1.0.4" }
if (-not $BuildNumber) { $BuildNumber = "67" }
$VersionNum = "$Version.$BuildNumber"

Write-Host "Target Version:      $Version (Build $BuildNumber)" -ForegroundColor Green
Write-Host "Version Numeric:     $VersionNum" -ForegroundColor Green
Write-Host "Desktop Directory:   $DesktopDir" -ForegroundColor Green

# 1. Toolchain Verification
Write-Host "`n[1/7] Verifying Build Toolchains..." -ForegroundColor Yellow
$vsInstalled = $false
try {
    $doctorOutput = flutter doctor -v
    if ($doctorOutput -match "Visual Studio not installed") {
        Write-Host "  [NOTICE] Visual Studio C++ toolchain is NOT installed on this machine." -ForegroundColor DarkYellow
        Write-Host "  Flutter requires Visual Studio 2019/2022 with 'Desktop development with C++' to compile native binaries." -ForegroundColor DarkYellow
    } else {
        $vsInstalled = $true
        Write-Host "  Visual Studio C++ toolchain detected." -ForegroundColor Green
    }
} catch {
    Write-Host "  Failed to execute flutter doctor: $_" -ForegroundColor Red
}

# Locate signtool.exe if available
$signtool = Get-Command "signtool.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
if (-not $signtool) {
    $sdkPaths = @(
        "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe",
        "${env:ProgramFiles}\Windows Kits\10\bin\*\x64\signtool.exe"
    )
    foreach ($pattern in $sdkPaths) {
        $found = Resolve-Path $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            $signtool = $found.Path
            break
        }
    }
}
if ($signtool) {
    Write-Host "  Windows SDK SignTool detected: $signtool" -ForegroundColor Green
} else {
    Write-Host "  SignTool.exe not found in PATH or standard Windows Kits locations." -ForegroundColor DarkYellow
}

# 2. Automated Quality Gate: Tests & Analysis
if (-not $SkipBuild) {
    Write-Host "`n[2/7] Executing Quality Gate (flutter test)..." -ForegroundColor Yellow
    $testResult = flutter test test/unit test/widget_test.dart
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Flutter unit and widget tests failed!" -ForegroundColor Red
        exit 1
    } else {
        Write-Host "  [PASS] All unit and widget tests passed successfully." -ForegroundColor Green
    }
} else {
    Write-Host "`n[2/7] Quality Gate Skipped: SkipBuild flag set." -ForegroundColor DarkYellow
}

# 3. Release Compilation
if (-not $SkipBuild -and $vsInstalled) {
    Write-Host "`n[3/7] Compiling Flutter Windows Release Executable..." -ForegroundColor Yellow
    flutter build windows --release
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Release compilation failed!" -ForegroundColor Red
        exit 1
    }
} elseif (-not $vsInstalled) {
    Write-Host "`n[3/7] Compilation Skipped: Local MSVC toolchain not present." -ForegroundColor DarkYellow
}

# 4. Dependency & Binary Audit
Write-Host "`n[4/7] Auditing Binaries & Assembling Distribution Tree..." -ForegroundColor Yellow
$ReleaseDir = Join-Path $DesktopDir "build\windows\x64\runner\Release"
$DistDir = Join-Path $DesktopDir "build\dist\LofiQ-v$Version-Windows"

if (Test-Path $ReleaseDir) {
    Write-Host "  Release directory found: $ReleaseDir" -ForegroundColor Green

    # Create clean distribution directory
    if (Test-Path $DistDir) { Remove-Item -Recurse -Force $DistDir }
    New-Item -ItemType Directory -Path $DistDir -Force | Out-Null

    # Ensure executable exists and is named LofiQ.exe
    $exeSource = Join-Path $ReleaseDir "LofiQ.exe"
    if (-not (Test-Path $exeSource)) {
        $legacyExe = Join-Path $ReleaseDir "desktop.exe"
        if (Test-Path $legacyExe) {
            Copy-Item $legacyExe $exeSource -Force
        }
    }
    if (Test-Path $exeSource) {
        Copy-Item $exeSource (Join-Path $DistDir "LofiQ.exe") -Force
        Write-Host "  Copied primary executable: LofiQ.exe" -ForegroundColor Green
    } else {
        Write-Host "  [ERROR] LofiQ.exe executable not found in $ReleaseDir!" -ForegroundColor Red
    }

    # Copy all DLL dependencies from Release directory
    Get-ChildItem -Path $ReleaseDir -Filter "*.dll" | ForEach-Object {
        Copy-Item $_.FullName $DistDir -Force
        Write-Host "  Copied DLL: $($_.Name)" -ForegroundColor Gray
    }

    # Guarantee libmpv-2.dll is bundled
    $rootMpv = Join-Path $DesktopDir "libmpv-2.dll"
    $distMpv = Join-Path $DistDir "libmpv-2.dll"
    if ((Test-Path $rootMpv) -and -not (Test-Path $distMpv)) {
        Copy-Item $rootMpv $DistDir -Force
        Write-Host "  Bundled native libmpv-2.dll from project root." -ForegroundColor Green
    }

    # Also copy root libmpv into runner Release folder so Inno Setup picks it up
    $releaseMpv = Join-Path $ReleaseDir "libmpv-2.dll"
    if ((Test-Path $rootMpv) -and -not (Test-Path $releaseMpv)) {
        Copy-Item $rootMpv $releaseMpv -Force
        Write-Host "  Placed libmpv-2.dll in Release runner directory for installer packaging." -ForegroundColor Green
    }

    # Copy data directory (assets, fonts, ICU tables)
    $dataDir = Join-Path $ReleaseDir "data"
    if (Test-Path $dataDir) {
        Copy-Item -Recurse $dataDir (Join-Path $DistDir "data") -Force
        Write-Host "  Copied Flutter data assets." -ForegroundColor Green
    }

    # Write Portable Documentation & Mark of the Web Guide
    $readmeContent = @"
========================================================================
LofiQ Desktop Music Player - Portable Edition (v$Version)
Website: https://lofiq-web.vercel.app/
Repository: https://github.com/itsafxal/LofiQ-Music
========================================================================

ABOUT PORTABLE EDITION
----------------------
This package is a standalone, fully self-contained distribution of LofiQ
Music Player for 64-bit Windows 10 and Windows 11. It requires no installation
and does not require administrator privileges.

HOW TO RUN:
1. Extract the entire ZIP archive to a folder of your choice (e.g. C:\Users\<You>\LofiQ).
2. Double-click "LofiQ.exe" to start listening.

WINDOWS 11 SMART APP CONTROL & SMARTSCREEN NOTE:
------------------------------------------------
When you download ZIP archives using a web browser, Windows marks the file
with the "Mark of the Web" (Zone.Identifier Alternate Data Stream).

If Windows 11 Smart App Control (SAC) or Windows Defender SmartScreen blocks
execution because this open-source build does not yet have established Microsoft
cloud reputation:

1. Right-click the downloaded "LofiQ-v$Version-Windows-Portable.zip" BEFORE extracting.
2. Select "Properties".
3. At the bottom of the "General" tab, check the "Unblock" checkbox.
4. Click "Apply", then "OK".
5. Extract the ZIP and launch "LofiQ.exe".

Alternatively, unblock via PowerShell:
   Unblock-File -Path .\LofiQ-v$Version-Windows-Portable.zip

SYSTEM REQUIREMENTS:
--------------------
- OS: Windows 10 or Windows 11 (64-bit x64)
- Runtime: Microsoft Visual C++ 2015-2022 Redistributable (x64)
  If missing, download official runtime from Microsoft:
  https://aka.ms/vs/17/release/vc_redist.x64.exe
- Audio: Core Audio / WASAPI compatible audio output device

FEATURES INCLUDED:
------------------
- True Dual-Player Seamless & Gapless Playback Engine (libmpv / media_kit)
- 10-Band Graphic Equalizer, Bass Boost & Spatializer
- Windows System Media Transport Controls (SMTC) & Hardware Media Keys
- Synchronized LRCLIB Lyrics & Desktop Mini-Player
========================================================================
"@
    Set-Content -Path (Join-Path $DistDir "README_PORTABLE.txt") -Value $readmeContent -Encoding UTF8

    # Create portable ZIP archive
    $distParent = Join-Path $DesktopDir "build\dist"
    if (-not (Test-Path $distParent)) { New-Item -ItemType Directory -Path $distParent -Force | Out-Null }
    $zipPath = Join-Path $distParent "LofiQ-v$Version-Windows-Portable.zip"
    Compress-Archive -Path "$DistDir\*" -DestinationPath $zipPath -Force
    Write-Host "  Created portable distribution: $zipPath" -ForegroundColor Green
} else {
    Write-Host "  Release directory does not yet exist ($ReleaseDir). Build must be executed on a machine with MSVC." -ForegroundColor DarkYellow
}

# 5. Whole-Binary Authenticode Code Signing (Priority 1-5)
Write-Host "`n[5/7] Authenticode Code-Signing Stage..." -ForegroundColor Yellow
$signingConfigured = $false

if ($CertPath -and (Test-Path $CertPath)) {
    Write-Host "  Code-signing certificate detected: $CertPath" -ForegroundColor Green
    $signingConfigured = $true

    # Function to sign a file using signtool or PowerShell Set-AuthenticodeSignature
    function Sign-TargetFile {
        param (
            [string]$TargetFile,
            [string]$FileDescription = "LofiQ Desktop Music Player"
        )
        if (-not (Test-Path $TargetFile)) { return }

        if ($signtool) {
            Write-Host "    [SignTool] Signing $(Split-Path $TargetFile -Leaf)..." -ForegroundColor Cyan
            $argsList = @("sign", "/fd", "SHA256", "/f", $CertPath)
            if ($CertPassword) {
                $argsList += @("/p", $CertPassword)
            }
            $argsList += @("/tr", $TimestampServer, "/td", "SHA256", "/d", $FileDescription, "/du", "https://lofiq-web.vercel.app/", $TargetFile)
            & $signtool $argsList
            if ($LASTEXITCODE -ne 0) {
                $fallbackServer = "http://timestamp.sectigo.com"
                Write-Host "    [SignTool] Retrying with fallback timestamp server: $fallbackServer..." -ForegroundColor DarkYellow
                $argsList[7] = $fallbackServer
                & $signtool $argsList
            }
        } else {
            Write-Host "    [PowerShell] Signing $(Split-Path $TargetFile -Leaf)..." -ForegroundColor Cyan
            $secPass = ConvertTo-SecureString $CertPassword -AsPlainText -Force
            $certObj = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($CertPath, $secPass)
            Set-AuthenticodeSignature -FilePath $TargetFile -Certificate $certObj -HashAlgorithm SHA256 -TimestampServer $TimestampServer
        }

        # Signature verification check
        $sigInfo = Get-AuthenticodeSignature -FilePath $TargetFile
        Write-Host "      Status: $($sigInfo.Status) | Signer: $($sigInfo.SignerCertificate.Subject)" -ForegroundColor Gray
    }

    # Sign all distributed binaries (Whole App rule for Smart App Control compliance)
    if (Test-Path $ReleaseDir) {
        Write-Host "  Signing all binaries in runner Release directory..." -ForegroundColor Cyan
        Get-ChildItem -Path $ReleaseDir -Filter "*.dll" | ForEach-Object { Sign-TargetFile $_.FullName }
        $mainExe = Join-Path $ReleaseDir "LofiQ.exe"
        if (Test-Path $mainExe) { Sign-TargetFile $mainExe }
    }
} else {
    Write-Host "  [NOTICE] Production code-signing certificate was not provided." -ForegroundColor DarkYellow
    Write-Host "  Binary output will be UNSIGNED." -ForegroundColor DarkYellow
    Write-Host "  Windows 11 Smart App Control will require user MotW unblocking or a trusted certificate." -ForegroundColor DarkYellow
}

# 6. Inno Setup Compilation
Write-Host "`n[6/7] Checking for Inno Setup (ISCC.exe)..." -ForegroundColor Yellow
$iscc = Get-Command "ISCC.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
if (-not $iscc) {
    $commonInnoPaths = @(
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
        "${env:ProgramFiles}\Inno Setup 6\ISCC.exe"
    )
    foreach ($p in $commonInnoPaths) {
        if (Test-Path $p) {
            $iscc = $p
            break
        }
    }
}

if ($iscc -and (Test-Path $ReleaseDir)) {
    Write-Host "  Inno Setup detected: $iscc" -ForegroundColor Green
    $issFile = Join-Path $DesktopDir "installer\lofiq_setup.iss"
    Write-Host "  Compiling installer..." -ForegroundColor Yellow

    $isccArgs = @(
        "/DMyAppVersion=$Version",
        "/DMyAppVersionNum=$VersionNum"
    )

    if ($signingConfigured -and $signtool) {
        Write-Host "  Configuring Inno Setup SignTool for signed uninstaller and setup..." -ForegroundColor Cyan
        $signCommand = "`"$signtool`" sign /fd SHA256 /f `"$CertPath`""
        if ($CertPassword) {
            $signCommand += " /p `"$CertPassword`""
        }
        $signCommand += " /tr $TimestampServer /td SHA256 /d `"LofiQ Desktop Setup`" `$f"
        $isccArgs += "/DSignInstaller=1"
        $isccArgs += "/Scustom_sign_tool=$signCommand"
    }

    $isccArgs += $issFile
    & $iscc $isccArgs

    if ($LASTEXITCODE -eq 0) {
        $finalInstaller = Join-Path $DesktopDir "build\installer\LofiQ-v$Version-Windows-Setup.exe"
        Write-Host "  [SUCCESS] Installer compiled at $finalInstaller" -ForegroundColor Green

        # If signed, ensure the compiled installer is also signed
        if ($signingConfigured) {
            Sign-TargetFile $finalInstaller "LofiQ Desktop Setup Installer"
        }
    } else {
        Write-Host "  [WARN] Inno Setup compilation returned exit code $LASTEXITCODE" -ForegroundColor DarkYellow
    }
} else {
    Write-Host "  Inno Setup compiler not found or release directory not built." -ForegroundColor Gray
    Write-Host "  Installer script is ready at: desktop\installer\lofiq_setup.iss" -ForegroundColor Cyan
}

# 7. Checksum Generation (SHA-256)
Write-Host "`n[7/7] Generating Cryptographic Checksums (SHA-256)..." -ForegroundColor Yellow
$checksums = @()
$installerExe = Join-Path $DesktopDir "build\installer\LofiQ-v$Version-Windows-Setup.exe"
$portableZip = Join-Path $DesktopDir "build\dist\LofiQ-v$Version-Windows-Portable.zip"

if (Test-Path $installerExe) {
    $hash = (Get-FileHash -Path $installerExe -Algorithm SHA256).Hash.ToLower()
    $checksums += "$hash  LofiQ-v$Version-Windows-Setup.exe"
    Write-Host "  Installer SHA-256: $hash" -ForegroundColor Green
}

if (Test-Path $portableZip) {
    $hash = (Get-FileHash -Path $portableZip -Algorithm SHA256).Hash.ToLower()
    $checksums += "$hash  LofiQ-v$Version-Windows-Portable.zip"
    Write-Host "  Portable ZIP SHA-256: $hash" -ForegroundColor Green
}

if ($checksums.Count -gt 0) {
    $checksumFileDist = Join-Path $DesktopDir "build\dist\SHA256SUMS.txt"
    $checksumFileInst = Join-Path $DesktopDir "build\installer\SHA256SUMS.txt"
    $checksumContent = $checksums -join "`n"
    Set-Content -Path $checksumFileDist -Value $checksumContent -Encoding UTF8
    if (Test-Path (Join-Path $DesktopDir "build\installer")) {
        Set-Content -Path $checksumFileInst -Value $checksumContent -Encoding UTF8
    }
    Write-Host "  Written SHA256SUMS.txt successfully." -ForegroundColor Green
}

Write-Host "`n=== Release packaging workflow completed successfully. ===" -ForegroundColor Cyan
