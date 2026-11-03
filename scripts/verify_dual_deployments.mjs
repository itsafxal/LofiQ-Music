import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import https from 'node:https';

const ROOT_DIR = process.cwd();
const MANIFEST_PATH = path.join(ROOT_DIR, 'web', 'version.json');
const APK_PATH = path.join(ROOT_DIR, 'web', 'downloads', 'LofiQ.apk');

const PRIMARY_URL = 'https://lofiq-web.vercel.app';
const SECONDARY_URL = 'https://mybackuplofiq.netlify.app';

console.log('======================================================================');
console.log('🌐 LOFIQ DUAL HOSTING RELEASE SYNCHRONIZATION AUDIT');
console.log('   Primary (Public):    ' + PRIMARY_URL);
console.log('   Secondary (Backup):  ' + SECONDARY_URL);
console.log('======================================================================');

// Read local authoritative release
if (!fs.existsSync(MANIFEST_PATH)) {
  console.error(`❌ Local manifest not found at ${MANIFEST_PATH}`);
  process.exit(1);
}

const localManifest = JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'));
const localApkStats = fs.existsSync(APK_PATH) ? fs.statSync(APK_PATH) : null;
const expectedChecksum = localManifest.checksum.toLowerCase();
const expectedCode = localManifest.versionCode;
const expectedName = localManifest.versionName;
const expectedSize = localApkStats ? localApkStats.size : null;

console.log(`📋 Authoritative Local Baseline (from Git commit):`);
console.log(`   versionCode:    ${expectedCode}`);
console.log(`   versionName:    ${expectedName}`);
console.log(`   APK Size:       ${expectedSize ? expectedSize + ' bytes' : 'N/A'}`);
console.log(`   SHA-256:        ${expectedChecksum}`);
console.log('----------------------------------------------------------------------\n');

function httpRequest(url, options = {}) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, options, (res) => {
      let data = [];
      res.on('data', chunk => data.push(chunk));
      res.on('end', () => {
        const buffer = Buffer.concat(data);
        resolve({
          statusCode: res.statusCode,
          headers: res.headers,
          body: buffer.toString('utf8'),
          rawBuffer: buffer
        });
      });
    });
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error(`Timeout after ${options.timeout || 15000}ms requesting ${url}`));
    });
    req.end();
  });
}

function streamDownloadAndComputeSha256(url) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    let totalBytes = 0;

    const req = https.get(url, (res) => {
      if (res.statusCode !== 200) {
        return reject(new Error(`HTTP ${res.statusCode} from ${url}`));
      }
      res.on('data', (chunk) => {
        hash.update(chunk);
        totalBytes += chunk.length;
      });
      res.on('end', () => {
        resolve({
          sha256: hash.digest('hex').toLowerCase(),
          bytesReceived: totalBytes,
          headers: res.headers
        });
      });
    });

    req.on('error', reject);
    req.setTimeout(45000, () => {
      req.destroy();
      reject(new Error(`Timeout downloading ${url}`));
    });
  });
}

async function probeHost(hostName, baseUrl) {
  const result = {
    hostName,
    baseUrl,
    homeStatus: null,
    manifestStatus: null,
    manifest: null,
    apkHeadStatus: null,
    apkContentLength: null,
    apkSha256: null,
    apkSha256Match: false,
    errors: []
  };

  try {
    // 1. Probe Home
    const home = await httpRequest(`${baseUrl}/`, { method: 'GET', timeout: 15000 });
    result.homeStatus = home.statusCode;
    if (home.statusCode !== 200) {
      result.errors.push(`Home page returned HTTP ${home.statusCode}`);
    }
  } catch (e) {
    result.errors.push(`Home page probe failed: ${e.message}`);
  }

  try {
    // 2. Probe Manifest
    const manifestRes = await httpRequest(`${baseUrl}/version.json?_nocache=${Date.now()}`, {
      method: 'GET',
      timeout: 15000,
      headers: {
        'Cache-Control': 'no-cache, no-store, must-revalidate',
        'Pragma': 'no-cache'
      }
    });
    result.manifestStatus = manifestRes.statusCode;
    if (manifestRes.statusCode === 200) {
      try {
        result.manifest = JSON.parse(manifestRes.body);
      } catch (e) {
        result.errors.push(`version.json returned invalid JSON: ${e.message}`);
      }
    } else {
      result.errors.push(`version.json returned HTTP ${manifestRes.statusCode}`);
    }
  } catch (e) {
    result.errors.push(`version.json probe failed: ${e.message}`);
  }

  try {
    // 3. Probe APK HEAD
    const apkHead = await httpRequest(`${baseUrl}/downloads/LofiQ.apk`, { method: 'HEAD', timeout: 15000 });
    result.apkHeadStatus = apkHead.statusCode;
    if (apkHead.statusCode === 200) {
      const len = parseInt(apkHead.headers['content-length'] || '0', 10);
      result.apkContentLength = len;
    } else {
      result.errors.push(`APK HEAD returned HTTP ${apkHead.statusCode}`);
    }
  } catch (e) {
    result.errors.push(`APK HEAD probe failed: ${e.message}`);
  }

  try {
    // 4. Download APK Stream & compute full SHA-256
    console.log(`⏳ Downloading & computing SHA-256 for ${hostName} APK...`);
    const apkStream = await streamDownloadAndComputeSha256(`${baseUrl}/downloads/LofiQ.apk?_nocache=${Date.now()}`);
    result.apkSha256 = apkStream.sha256;
    result.apkSha256Match = (result.apkSha256 === expectedChecksum);
    if (!result.apkSha256Match) {
      result.errors.push(`APK SHA-256 mismatch: got ${result.apkSha256}, expected ${expectedChecksum}`);
    }
  } catch (e) {
    result.errors.push(`APK stream SHA-256 computation failed: ${e.message}`);
  }

  return result;
}

const sleep = ms => new Promise(res => setTimeout(res, ms));

async function run() {
  const maxAttempts = process.env.MAX_VERIFY_ATTEMPTS ? parseInt(process.env.MAX_VERIFY_ATTEMPTS, 10) : (process.env.CI ? 6 : 1);
  let attempt = 0;
  let finalPrimary = null;
  let finalSecondary = null;
  let finalConsistent = false;
  let finalPrimaryMatchesGit = false;
  let finalSecondaryMatchesGit = false;
  let finalDiscrepancies = [];

  while (attempt < maxAttempts) {
    attempt++;
    if (attempt > 1) {
      console.log(`\n🔄 Retrying verification (Attempt ${attempt}/${maxAttempts}) in 10s to allow edge cache propagation...`);
      await sleep(10000);
    }

    console.log(`📡 Probing endpoints in parallel (Attempt ${attempt}/${maxAttempts})...\n`);
    const [primary, secondary] = await Promise.all([
      probeHost('Primary (Vercel)', PRIMARY_URL),
      probeHost('Secondary (Netlify)', SECONDARY_URL)
    ]);

    finalPrimary = primary;
    finalSecondary = secondary;

    let isConsistent = true;
    const discrepancies = [];

    if (primary.manifest?.versionCode !== secondary.manifest?.versionCode) {
      isConsistent = false;
      discrepancies.push(`versionCode divergence: Primary=${primary.manifest?.versionCode} vs Secondary=${secondary.manifest?.versionCode}`);
    }

    if (primary.manifest?.versionName !== secondary.manifest?.versionName) {
      isConsistent = false;
      discrepancies.push(`versionName divergence: Primary="${primary.manifest?.versionName}" vs Secondary="${secondary.manifest?.versionName}"`);
    }

    if (primary.manifest?.checksum?.toLowerCase() !== secondary.manifest?.checksum?.toLowerCase()) {
      isConsistent = false;
      discrepancies.push(`Manifest checksum divergence: Primary=${primary.manifest?.checksum} vs Secondary=${secondary.manifest?.checksum}`);
    }

    if (primary.apkSha256 !== secondary.apkSha256) {
      isConsistent = false;
      discrepancies.push(`APK binary SHA-256 divergence: Primary=${primary.apkSha256} vs Secondary=${secondary.apkSha256}`);
    }

    if (primary.apkContentLength !== secondary.apkContentLength) {
      isConsistent = false;
      discrepancies.push(`APK file size divergence: Primary=${primary.apkContentLength} vs Secondary=${secondary.apkContentLength}`);
    }

    const primaryMatchesGit = (primary.manifest?.versionCode === expectedCode && primary.apkSha256Match);
    const secondaryMatchesGit = (secondary.manifest?.versionCode === expectedCode && secondary.apkSha256Match);

    finalConsistent = isConsistent;
    finalPrimaryMatchesGit = primaryMatchesGit;
    finalSecondaryMatchesGit = secondaryMatchesGit;
    finalDiscrepancies = discrepancies;

    if (isConsistent && primaryMatchesGit && secondaryMatchesGit) {
      break;
    }
  }

  console.log('\n======================================================================');
  console.log('📊 AUDIT RESULTS SUMMARY');
  console.log('======================================================================\n');

  console.log(`[PRIMARY: Vercel - ${PRIMARY_URL}]`);
  console.log(`  • Home Page HTTP:        ${finalPrimary.homeStatus === 200 ? '✅ 200 OK' : '❌ ' + finalPrimary.homeStatus}`);
  console.log(`  • version.json HTTP:     ${finalPrimary.manifestStatus === 200 ? '✅ 200 OK' : '❌ ' + finalPrimary.manifestStatus}`);
  console.log(`  • versionCode:           ${finalPrimary.manifest?.versionCode ?? 'N/A'} (Expected: ${expectedCode})`);
  console.log(`  • versionName:           ${finalPrimary.manifest?.versionName ?? 'N/A'} (Expected: ${expectedName})`);
  console.log(`  • manifest checksum:     ${finalPrimary.manifest?.checksum ?? 'N/A'}`);
  console.log(`  • APK Content-Length:    ${finalPrimary.apkContentLength ?? 'N/A'} bytes (Expected: ${expectedSize})`);
  console.log(`  • APK SHA-256 Computed:  ${finalPrimary.apkSha256 ?? 'N/A'}`);
  console.log(`  • APK Integrity Match:   ${finalPrimary.apkSha256Match ? '✅ MATCH' : '❌ MISMATCH'}`);
  if (finalPrimary.errors.length > 0) {
    console.log(`  ⚠️ Issues Detected:`);
    finalPrimary.errors.forEach(e => console.log(`     - ${e}`));
  }
  console.log('');

  console.log(`[SECONDARY: Netlify - ${SECONDARY_URL}]`);
  console.log(`  • Home Page HTTP:        ${finalSecondary.homeStatus === 200 ? '✅ 200 OK' : '❌ ' + finalSecondary.homeStatus}`);
  console.log(`  • version.json HTTP:     ${finalSecondary.manifestStatus === 200 ? '✅ 200 OK' : '❌ ' + finalSecondary.manifestStatus}`);
  console.log(`  • versionCode:           ${finalSecondary.manifest?.versionCode ?? 'N/A'} (Expected: ${expectedCode})`);
  console.log(`  • versionName:           ${finalSecondary.manifest?.versionName ?? 'N/A'} (Expected: ${expectedName})`);
  console.log(`  • manifest checksum:     ${finalSecondary.manifest?.checksum ?? 'N/A'}`);
  console.log(`  • APK Content-Length:    ${finalSecondary.apkContentLength ?? 'N/A'} bytes (Expected: ${expectedSize})`);
  console.log(`  • APK SHA-256 Computed:  ${finalSecondary.apkSha256 ?? 'N/A'}`);
  console.log(`  • APK Integrity Match:   ${finalSecondary.apkSha256Match ? '✅ MATCH' : '❌ MISMATCH'}`);
  if (finalSecondary.errors.length > 0) {
    console.log(`  ⚠️ Issues Detected:`);
    finalSecondary.errors.forEach(e => console.log(`     - ${e}`));
  }
  console.log('');

  console.log('======================================================================');
  console.log('⚖️  CROSS-ENDPOINT SYNCHRONIZATION VERDICT');
  console.log('======================================================================');

  if (finalConsistent && finalPrimaryMatchesGit && finalSecondaryMatchesGit) {
    console.log('🎉 PERFECT SYNCHRONIZATION CONFIRMED!');
    console.log('   Both Vercel (Primary) and Netlify (Secondary) serve 100% IDENTICAL');
    console.log(`   release assets matching Git commit (V${expectedName} - Build ${expectedCode}).`);
    process.exit(0);
  } else {
    console.error('❌ SYNCHRONIZATION FAILURE / DIVERGENCE DETECTED!');
    console.error(`   Primary matches Git:   ${finalPrimaryMatchesGit ? 'YES' : 'NO'}`);
    console.error(`   Secondary matches Git: ${finalSecondaryMatchesGit ? 'YES' : 'NO'}`);
    if (finalDiscrepancies.length > 0) {
      console.error('   Discrepancies:');
      finalDiscrepancies.forEach(d => console.error(`     • ${d}`));
    }
    process.exit(1);
  }
}

run().catch(err => {
  console.error(`Fatal verification execution error: ${err.message}`);
  process.exit(1);
});
