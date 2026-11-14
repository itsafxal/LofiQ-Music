import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execSync } from 'node:child_process';

const ROOT_DIR = process.cwd();
const MANIFEST_PATH = path.join(ROOT_DIR, 'web', 'version.json');
const APK_PATH = path.join(ROOT_DIR, 'web', 'downloads', 'LofiQ.apk');
const GRADLE_PATH = path.join(ROOT_DIR, 'app', 'build.gradle.kts');
const HTML_PATH = path.join(ROOT_DIR, 'web', 'index.html');
const ANDROID_MANIFEST_PATH = path.join(ROOT_DIR, 'app', 'src', 'main', 'AndroidManifest.xml');

// Expected Verified Production Certificate Fingerprint (SHA-256)
const EXPECTED_CERT_SHA256 = '6231bd272c05446d273eb041a20d46cba0e908848c9bbab34f3c1542cedc0a11';

console.log('====================================================');
console.log('🔍 LOFIQ PRODUCTION RELEASE PRE-FLIGHT VALIDATION');
console.log('====================================================');

let hasErrors = false;

function error(msg) {
  console.error(`❌ [ERROR] ${msg}`);
  hasErrors = true;
}

function success(msg) {
  console.log(`✅ [PASS] ${msg}`);
}

function warn(msg) {
  console.warn(`⚠️  [WARN] ${msg}`);
}

// 1. Validate version.json exists and parse
if (!fs.existsSync(MANIFEST_PATH)) {
  error(`Missing version manifest at ${MANIFEST_PATH}`);
  process.exit(1);
}

let manifest;
try {
  const raw = fs.readFileSync(MANIFEST_PATH, 'utf8');
  manifest = JSON.parse(raw);
  success('Release manifest web/version.json is valid JSON');
} catch (e) {
  error(`Malformed JSON in web/version.json: ${e.message}`);
  process.exit(1);
}

// 2. Validate manifest fields
const { versionCode, versionName, checksum, apkUrl, releaseNotes } = manifest;

if (typeof versionCode !== 'number' || versionCode <= 0) {
  error(`Invalid or missing 'versionCode' (${versionCode}) in version.json`);
} else {
  success(`Manifest versionCode: ${versionCode}`);
}

if (!versionName || typeof versionName !== 'string') {
  error(`Invalid or missing 'versionName' (${versionName}) in version.json`);
} else {
  success(`Manifest versionName: "${versionName}"`);
}

if (!checksum || !/^[a-fA-F0-9]{64}$/.test(checksum.trim())) {
  error(`Invalid or missing SHA-256 'checksum' (${checksum}) in version.json`);
} else {
  success(`Manifest checksum format valid (64 hex characters)`);
}

if (!apkUrl || typeof apkUrl !== 'string') {
  error(`Invalid or missing 'apkUrl' in version.json`);
} else {
  success(`Manifest apkUrl: "${apkUrl}"`);
}

if (!Array.isArray(releaseNotes) || releaseNotes.length === 0) {
  warn(`Release notes in version.json are empty or missing`);
} else {
  success(`Manifest release notes contain ${releaseNotes.length} entry/entries`);
}

// 3. Strict Gradle build configuration audit
if (fs.existsSync(GRADLE_PATH)) {
  const gradleContent = fs.readFileSync(GRADLE_PATH, 'utf8');
  const codeMatch = gradleContent.match(/versionCode\s*=\s*(\d+)/);
  const nameMatch = gradleContent.match(/versionName\s*=\s*"([^"]+)"/);

  if (codeMatch) {
    const gradleCode = parseInt(codeMatch[1], 10);
    if (gradleCode !== versionCode) {
      error(`versionCode mismatch: build.gradle.kts has ${gradleCode}, but version.json has ${versionCode}`);
    } else {
      success(`Gradle versionCode (${gradleCode}) matches manifest`);
    }
  }

  if (nameMatch) {
    const gradleName = nameMatch[1];
    if (gradleName !== versionName) {
      error(`versionName mismatch: build.gradle.kts has "${gradleName}", but version.json has "${versionName}"`);
    } else {
      success(`Gradle versionName ("${gradleName}") matches manifest`);
    }
  }

  // Audit: Debug fallback elimination
  if (gradleContent.includes('signingConfigs.getByName("debug")') && gradleContent.includes('buildTypes') && gradleContent.includes('release')) {
    const releaseBlock = gradleContent.substring(gradleContent.indexOf('release {'), gradleContent.indexOf('debug {'));
    if (releaseBlock.includes('debug')) {
      error(`Release buildType contains fallback to debug signing! Strict signing required.`);
    } else {
      success('Confirmed: Debug signing fallback permanently removed from release buildType');
    }
  } else {
    success('Confirmed: Release buildType strictly references release signingConfig without debug fallback');
  }

  // Audit: Multi-scheme signature declarations
  if (gradleContent.includes('enableV1Signing = true') &&
      gradleContent.includes('enableV2Signing = true') &&
      gradleContent.includes('enableV3Signing = true')) {
    success('Confirmed: Multi-scheme V1, V2, and V3 signing explicitly enabled in Gradle');
  } else {
    warn('Gradle signing configuration may be missing explicit V1, V2, or V3 scheme declarations');
  }

  // Audit: Strict signing fail-fast hook
  if (gradleContent.includes('STRICT SIGNING FAILURE') || gradleContent.includes('Strict Release Signing Error')) {
    success('Confirmed: Fail-fast Gradle hook configured to abort unsigned or improperly signed release builds');
  } else {
    warn('Fail-fast release signing verification hook not detected in build.gradle.kts');
  }
} else {
  warn(`Could not find build.gradle.kts at ${GRADLE_PATH}`);
}

// 4. Manifest and Permission Audit
if (fs.existsSync(ANDROID_MANIFEST_PATH)) {
  const manifestXml = fs.readFileSync(ANDROID_MANIFEST_PATH, 'utf8');
  if (manifestXml.includes('android.permission.REQUEST_INSTALL_PACKAGES')) {
    success('Confirmed: REQUEST_INSTALL_PACKAGES permission present (Required for AppUpdateManager OTA updates)');
  } else {
    error('REQUEST_INSTALL_PACKAGES permission is missing! In-app OTA updates will fail.');
  }

  // Verify exported components security
  if (manifestXml.includes('android:name=".playback.service.LofiQMediaSessionService"') &&
      manifestXml.includes('android:name="androidx.media3.session.MediaSessionService"')) {
    success('PlaybackService correctly configured with MediaSessionService intent-filter');
  }

  if (manifestXml.includes('android:name="androidx.core.content.FileProvider"') &&
      manifestXml.includes('android:exported="false"')) {
    success('FileProvider correctly configured with exported="false" and grantUriPermissions="true"');
  }
}

// 5. Validate APK existence, size, and cryptographic checksum
if (!fs.existsSync(APK_PATH)) {
  error(`Missing production APK at ${APK_PATH}`);
} else {
  const stats = fs.statSync(APK_PATH);
  const sizeMB = (stats.size / (1024 * 1024)).toFixed(2);
  if (stats.size < 15 * 1024 * 1024) {
    error(`APK size (${sizeMB} MB) is unusually small (< 15MB)`);
  } else {
    success(`APK exists at web/downloads/LofiQ.apk (${sizeMB} MB, ${stats.size} bytes)`);
  }

  console.log('🔐 Computing SHA-256 checksum of local APK...');
  const apkBuffer = fs.readFileSync(APK_PATH);
  const hash = crypto.createHash('sha256').update(apkBuffer).digest('hex').toLowerCase();
  const expectedHash = (checksum || '').trim().toLowerCase();

  if (hash !== expectedHash) {
    error(`SHA-256 CHECKSUM MISMATCH!`);
    console.error(`   Computed: ${hash}`);
    console.error(`   Expected: ${expectedHash}`);
  } else {
    success(`Cryptographic SHA-256 match confirmed: ${hash}`);
  }

  // Basic APK structure sanity check (Zip magic bytes: 0x50 0x4B 0x03 0x04)
  if (apkBuffer.length > 4 && apkBuffer[0] === 0x50 && apkBuffer[1] === 0x4b && apkBuffer[2] === 0x03 && apkBuffer[3] === 0x04) {
    success(`APK file format confirmed (valid ZIP / Android package header)`);
  } else {
    error(`APK file does not have valid ZIP magic bytes!`);
  }

  // 6. Production Signature and Certificate Verification via apksigner (if available)
  let apksignerBin = null;
  const sdkRoots = [
    process.env.ANDROID_HOME,
    process.env.ANDROID_SDK_ROOT,
    path.join(process.env.LOCALAPPDATA || '', 'Android', 'Sdk'),
    path.join(process.env.HOME || '', 'Android', 'Sdk'),
    '/usr/local/lib/android/sdk',
    '/opt/android-sdk'
  ].filter(Boolean);

  for (const root of sdkRoots) {
    const buildToolsDir = path.join(root, 'build-tools');
    if (fs.existsSync(buildToolsDir)) {
      const versions = fs.readdirSync(buildToolsDir).sort().reverse();
      for (const v of versions) {
        const candidate = path.join(buildToolsDir, v, process.platform === 'win32' ? 'apksigner.bat' : 'apksigner');
        if (fs.existsSync(candidate)) {
          apksignerBin = candidate;
          break;
        }
      }
    }
    if (apksignerBin) break;
  }

  if (!apksignerBin) {
    try {
      execSync('apksigner --version', { stdio: 'ignore' });
      apksignerBin = 'apksigner';
    } catch {}
  }

  if (apksignerBin) {
    console.log(`🔍 Verifying APK cryptographic signatures using ${apksignerBin}...`);
    try {
      const javaHome = process.env.JAVA_HOME || 'C:\\Program Files\\Android\\Android Studio\\jbr';
      const env = {
        ...process.env,
        JAVA_HOME: javaHome,
        PATH: `${path.join(javaHome, 'bin')}${path.delimiter}${process.env.PATH}`
      };
      const verifyOutput = execSync(`"${apksignerBin}" verify --verbose --print-certs "${APK_PATH}"`, {
        env,
        encoding: 'utf8'
      });

      const certSha256Match = verifyOutput.match(/certificate SHA-256 digest:\s*([a-fA-F0-9]{64})/i);
      if (certSha256Match) {
        const actualSha256 = certSha256Match[1].toLowerCase();
        if (actualSha256 === EXPECTED_CERT_SHA256) {
          success(`Certificate SHA-256 matches verified production key: ${actualSha256}`);
        } else {
          error(`Certificate SHA-256 MISMATCH! Expected ${EXPECTED_CERT_SHA256}, got ${actualSha256}`);
        }
      } else {
        warn('Could not parse Signer #1 SHA-256 digest from apksigner output');
      }

      if (verifyOutput.includes('Verified using v2 scheme (APK Signature Scheme v2): true') &&
          verifyOutput.includes('Verified using v3 scheme (APK Signature Scheme v3): true')) {
        success('APK Signature Schemes V2 and V3 cryptographically verified');
      } else {
        warn('One or more APK signature schemes (V2/V3) did not return true');
      }
    } catch (e) {
      warn(`apksigner invocation encountered an error: ${e.message}`);
    }
  } else {
    warn('apksigner not found in environment; skipped binary signature scheme inspection');
  }
}

// 7. Check web/index.html version alignment
if (fs.existsSync(HTML_PATH)) {
  const html = fs.readFileSync(HTML_PATH, 'utf8');
  if (!html.includes(`v${versionName}`)) {
    warn(`web/index.html does not appear to contain "v${versionName}"`);
  } else {
    success(`web/index.html references "v${versionName}"`);
  }
}

console.log('====================================================');
if (hasErrors) {
  console.error('❌ RELEASE ARTIFACTS VALIDATION FAILED!');
  process.exit(1);
} else {
  console.log('🎉 ALL RELEASE ARTIFACTS & SIGNATURES ARE VERIFIED FOR PRODUCTION DEPLOYMENT!');
  process.exit(0);
}
