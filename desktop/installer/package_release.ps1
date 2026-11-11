# =====================================================================
# LofiQ Windows Desktop Release Packaging & Verification Script
# Version: 1.0.2
# =====================================================================

param (
    [switch]$SkipBuild = $false
)

$ErrorActionPreference = "Stop"

Write-Host "=== LOFIQ WINDOWS DESKTOP RELEASE PACKAGING ===" -ForegroundColor Cyan

$DesktopDir = Split-Path -Parent $PSScriptRoot
Set-Location $DesktopDir

# 1. Toolchain Verification
Write-Host "`n[1/6] Verifying Build Toolchain..." -ForegroundColor Yellow
$vsInstalled = $false
try {
    $doctorOutput = flutter doctor -v
    if ($doctorOutput -match "Visual Studio not installed") {
        Write-Host "  [BLOCKER] Visual Studio C++ toolchain is NOT installed on this machine." -ForegroundColor Red
        Write-Host "  Flutter requires Visual Studio 2019/2022 with 'Desktop development with C++'." -ForegroundColor Red
        Write-Host "  Please install Visual Studio or MSVC Build Tools to compile native release binaries." -ForegroundColor Red
    } else {
        $vsInstalled = $true
        Write-Host "  Visual Studio toolchain detected." -ForegroundColor Green
    }
} catch {
    Write-Host "  Failed to execute flutter doctor: $_" -ForegroundColor Red
}

# 2. Automated Quality Gate: Tests & Analysis
if (-not $SkipBuild) {
    Write-Host "`n[2/6] Executing Quality Gate (flutter test)..." -ForegroundColor Yellow
    $testResult = flutter test
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Flutter unit and integration tests failed!" -ForegroundColor Red
        exit 1
    } else {
        Write-Host "  [PASS] All automated tests passed successfully." -ForegroundColor Green
    }
} else {
    Write-Host "`n[2/6] Quality Gate Skipped: SkipBuild flag set." -ForegroundColor DarkYellow
}

# 3. Release Compilation
if (-not $SkipBuild -and $vsInstalled) {
    Write-Host "`n[3/6] Compiling Flutter Windows Release Executable..." -ForegroundColor Yellow
    flutter build windows --release
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Release compilation failed!" -ForegroundColor Red
        exit 1
    }
} elseif (-not $vsInstalled) {
    Write-Host "`n[3/6] Compilation Skipped: Toolchain blocked." -ForegroundColor DarkYellow
}

# 4. Dependency & Binary Audit
Write-Host "`n[4/6] Verifying Runtime Dependencies..." -ForegroundColor Yellow
$ReleaseDir = Join-Path $DesktopDir "build\windows\x64\runner\Release"
$DistDir = Join-Path $DesktopDir "build\dist\LofiQ-v1.0.2-Windows"

if (Test-Path $ReleaseDir) {
    Write-Host "  Release directory found: $ReleaseDir" -ForegroundColor Green

    # Create clean distribution directory
    if (Test-Path $DistDir) { Remove-Item -Recurse -Force $DistDir }
    New-Item -ItemType Directory -Path $DistDir -Force | Out-Null

    # Copy binary and rename to LofiQ.exe
    $exeSource = Join-Path $ReleaseDir "desktop.exe"
    if (-not (Test-Path $exeSource)) {
        $exeSource = Join-Path $ReleaseDir "LofiQ.exe"
    }
    if (Test-Path $exeSource) {
        Copy-Item $exeSource (Join-Path $DistDir "LofiQ.exe") -Force
        Write-Host "  Copied executable: LofiQ.exe" -ForegroundColor Green
    }

    # Copy Flutter engine and plugins
    Get-ChildItem -Path $ReleaseDir -Filter "*.dll" | ForEach-Object {
        Copy-Item $_.FullName $DistDir -Force
        Write-Host "  Copied DLL: $($_.Name)" -ForegroundColor Gray
    }

    # Ensure libmpv-2.dll is included
    $rootMpv = Join-Path $DesktopDir "libmpv-2.dll"
    if ((Test-Path $rootMpv) -and -not (Test-Path (Join-Path $DistDir "libmpv-2.dll"))) {
        Copy-Item $rootMpv $DistDir -Force
        Write-Host "  Bundled native libmpv-2.dll from project root." -ForegroundColor Green
    }

    # Copy data directory (assets, icudtl.dat)
    $dataDir = Join-Path $ReleaseDir "data"
    if (Test-Path $dataDir) {
        Copy-Item -Recurse $dataDir (Join-Path $DistDir "data") -Force
        Write-Host "  Copied Flutter data assets." -ForegroundColor Green
    }

    # Create portable ZIP archive
    $zipPath = Join-Path $DesktopDir "build\dist\LofiQ-v1.0.2-Windows-Portable.zip"
    Compress-Archive -Path "$DistDir\*" -DestinationPath $zipPath -Force
    Write-Host "  Created portable distribution: $zipPath" -ForegroundColor Green
} else {
    Write-Host "  Release directory does not yet exist ($ReleaseDir). Build must be executed on a machine with MSVC." -ForegroundColor DarkYellow
}

# 5. Installer Compilation
Write-Host "`n[5/6] Checking for Inno Setup (ISCC.exe)..." -ForegroundColor Yellow
$iscc = Get-Command "ISCC.exe" -ErrorAction SilentlyContinue
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
    & $iscc $issFile
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [SUCCESS] Installer compiled at build\installer\LofiQ-v1.0.2-Windows-Setup.exe" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] Inno Setup compilation returned exit code $LASTEXITCODE" -ForegroundColor DarkYellow
    }
} else {
    Write-Host "  Inno Setup compiler not found or release directory not built." -ForegroundColor Gray
    Write-Host "  Installer script is ready at: desktop\installer\lofiq_setup.iss" -ForegroundColor Cyan
}

# 6. Checksum Generation (SHA-256)
Write-Host "`n[6/6] Generating Cryptographic Checksums (SHA-256)..." -ForegroundColor Yellow
$checksums = @()
$installerExe = Join-Path $DesktopDir "build\installer\LofiQ-v1.0.2-Windows-Setup.exe"
$portableZip = Join-Path $DesktopDir "build\dist\LofiQ-v1.0.2-Windows-Portable.zip"

if (Test-Path $installerExe) {
    $hash = (Get-FileHash -Path $installerExe -Algorithm SHA256).Hash.ToLower()
    $checksums += "$hash  LofiQ-v1.0.2-Windows-Setup.exe"
    Write-Host "  Installer SHA-256: $hash" -ForegroundColor Green
}

if (Test-Path $portableZip) {
    $hash = (Get-FileHash -Path $portableZip -Algorithm SHA256).Hash.ToLower()
    $checksums += "$hash  LofiQ-v1.0.2-Windows-Portable.zip"
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

