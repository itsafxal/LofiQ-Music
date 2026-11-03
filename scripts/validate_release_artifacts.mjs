import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const ROOT_DIR = process.cwd();
const MANIFEST_PATH = path.join(ROOT_DIR, 'web', 'version.json');
const APK_PATH = path.join(ROOT_DIR, 'web', 'downloads', 'LofiQ.apk');
const GRADLE_PATH = path.join(ROOT_DIR, 'app', 'build.gradle.kts');
const HTML_PATH = path.join(ROOT_DIR, 'web', 'index.html');

console.log('====================================================');
console.log('🔍 LOFIQ RELEASE ARTIFACTS PRE-FLIGHT VALIDATION');
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

// 3. Compare with app/build.gradle.kts
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
} else {
  warn(`Could not find build.gradle.kts at ${GRADLE_PATH}`);
}

// 4. Validate APK existence, size, and cryptographic checksum
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

  // 5. Basic APK structure sanity check (Zip magic bytes: 0x50 0x4B 0x03 0x04)
  if (apkBuffer.length > 4 && apkBuffer[0] === 0x50 && apkBuffer[1] === 0x4b && apkBuffer[2] === 0x03 && apkBuffer[3] === 0x04) {
    success(`APK file format confirmed (valid ZIP / Android package header)`);
  } else {
    error(`APK file does not have valid ZIP magic bytes!`);
  }
}

// 6. Check web/index.html version alignment
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
  console.log('🎉 ALL RELEASE ARTIFACTS ARE VALID & READY FOR DEPLOYMENT!');
  process.exit(0);
}

