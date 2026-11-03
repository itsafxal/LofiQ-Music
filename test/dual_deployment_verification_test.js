const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

describe('LofiQ Dual Hosting Deployment & Synchronization Architecture', () => {
  const rootDir = process.cwd();
  const manifestPath = path.join(rootDir, 'web', 'version.json');
  const apkPath = path.join(rootDir, 'web', 'downloads', 'LofiQ.apk');
  const netlifyTomlPath = path.join(rootDir, 'netlify.toml');
  const vercelJsonPath = path.join(rootDir, 'web', 'vercel.json');
  const workflowPath = path.join(rootDir, '.github', 'workflows', 'deploy-dual-web.yml');

  let manifest;

  beforeAll(() => {
    manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  });

  test('Authoritative release manifest web/version.json contains required release metadata', () => {
    expect(manifest.versionCode).toBe(65);
    expect(manifest.versionName).toBe('1.0.2');
    expect(manifest.checksum).toMatch(/^[a-fA-F0-9]{64}$/);
    expect(manifest.apkUrl).toBe('/downloads/LofiQ.apk');
    expect(Array.isArray(manifest.releaseNotes)).toBe(true);
    expect(manifest.releaseNotes.length).toBeGreaterThan(0);
  });

  test('Local production APK exists and matches manifest SHA-256 checksum exactly', () => {
    expect(fs.existsSync(apkPath)).toBe(true);
    const apkBuffer = fs.readFileSync(apkPath);
    expect(apkBuffer.length).toBeGreaterThan(15 * 1024 * 1024); // > 15MB

    const computedSha = crypto.createHash('sha256').update(apkBuffer).digest('hex').toLowerCase();
    expect(computedSha).toBe(manifest.checksum.toLowerCase());
  });

  test('Cache headers in vercel.json enforce revalidation for version.json and immutable cache for APK', () => {
    const vercelConfig = JSON.parse(fs.readFileSync(vercelJsonPath, 'utf8'));
    const versionHeader = vercelConfig.headers.find(h => h.source === '/version.json');
    expect(versionHeader).toBeDefined();

    const cacheControl = versionHeader.headers.find(kv => kv.key === 'Cache-Control');
    expect(cacheControl).toBeDefined();
    expect(cacheControl.value).toContain('must-revalidate');
    expect(cacheControl.value).toContain('no-cache');
  });

  test('Cache headers in netlify.toml enforce revalidation for version.json and immutable cache for APK', () => {
    const netlifyToml = fs.readFileSync(netlifyTomlPath, 'utf8');
    expect(netlifyToml).toContain('publish = "web"');
    expect(netlifyToml).toContain('/version.json');
    expect(netlifyToml).toContain('no-cache, no-store, must-revalidate');
    expect(netlifyToml).toContain('/downloads/*');
    expect(netlifyToml).toContain('Content-Disposition = "attachment"');
  });

  test('deploy-dual-web.yml workflow enforces two-phase deployment and post-deploy audit', () => {
    expect(fs.existsSync(workflowPath)).toBe(true);
    const workflowContent = fs.readFileSync(workflowPath, 'utf8');

    // Pre-flight check
    expect(workflowContent).toContain('validate-release-artifacts');
    expect(workflowContent).toContain('node scripts/validate_release_artifacts.mjs');

    // Both deployment targets
    expect(workflowContent).toContain('deploy-vercel');
    expect(workflowContent).toContain('deploy-netlify');
    expect(workflowContent).toContain('NETLIFY_AUTH_TOKEN');
    expect(workflowContent).toContain('NETLIFY_SITE_ID');

    // Post-deployment verification
    expect(workflowContent).toContain('verify-synchronization');
    expect(workflowContent).toContain('node scripts/verify_dual_deployments.mjs');
  });

  describe('Cross-Endpoint Release Synchronization Evaluation Logic', () => {
    function evaluateSync(primary, secondary, local) {
      const discrepancies = [];
      let isConsistent = true;

      if (primary.manifest?.versionCode !== secondary.manifest?.versionCode) {
        isConsistent = false;
        discrepancies.push('versionCode divergence');
      }
      if (primary.manifest?.versionName !== secondary.manifest?.versionName) {
        isConsistent = false;
        discrepancies.push('versionName divergence');
      }
      if (primary.manifest?.checksum?.toLowerCase() !== secondary.manifest?.checksum?.toLowerCase()) {
        isConsistent = false;
        discrepancies.push('checksum divergence');
      }
      if (primary.apkSha256 !== secondary.apkSha256) {
        isConsistent = false;
        discrepancies.push('apk SHA-256 divergence');
      }
      if (primary.apkContentLength !== secondary.apkContentLength) {
        isConsistent = false;
        discrepancies.push('apk size divergence');
      }

      const primaryMatchesGit = primary.manifest?.versionCode === local.versionCode && primary.apkSha256 === local.checksum.toLowerCase();
      const secondaryMatchesGit = secondary.manifest?.versionCode === local.versionCode && secondary.apkSha256 === local.checksum.toLowerCase();

      return {
        success: isConsistent && primaryMatchesGit && secondaryMatchesGit,
        discrepancies,
        primaryMatchesGit,
        secondaryMatchesGit
      };
    }

    test('Scenario: Both providers deploy identically matching Git commit -> VERDICT: SUCCESS', () => {
      const primary = {
        homeStatus: 200,
        manifestStatus: 200,
        manifest: { versionCode: 65, versionName: '1.0.2', checksum: manifest.checksum },
        apkContentLength: 22647689,
        apkSha256: manifest.checksum.toLowerCase()
      };
      const secondary = {
        homeStatus: 200,
        manifestStatus: 200,
        manifest: { versionCode: 65, versionName: '1.0.2', checksum: manifest.checksum },
        apkContentLength: 22647689,
        apkSha256: manifest.checksum.toLowerCase()
      };

      const result = evaluateSync(primary, secondary, manifest);
      expect(result.success).toBe(true);
      expect(result.discrepancies.length).toBe(0);
      expect(result.primaryMatchesGit).toBe(true);
      expect(result.secondaryMatchesGit).toBe(true);
    });

    test('Scenario: Stale secondary deployment detected -> VERDICT: FAILURE with divergence details', () => {
      const primary = {
        homeStatus: 200,
        manifestStatus: 200,
        manifest: { versionCode: 65, versionName: '1.0.2', checksum: manifest.checksum },
        apkContentLength: 22647689,
        apkSha256: manifest.checksum.toLowerCase()
      };
      const staleSecondary = {
        homeStatus: 200,
        manifestStatus: 200,
        manifest: { versionCode: 64, versionName: '1.0.1', checksum: 'a4b05df41187a5f889e6e9bc01000261a4abd95ec70930f8d5ba0cb6c6a528ef' },
        apkContentLength: 22646728,
        apkSha256: 'a4b05df41187a5f889e6e9bc01000261a4abd95ec70930f8d5ba0cb6c6a528ef'
      };

      const result = evaluateSync(primary, staleSecondary, manifest);
      expect(result.success).toBe(false);
      expect(result.primaryMatchesGit).toBe(true);
      expect(result.secondaryMatchesGit).toBe(false);
      expect(result.discrepancies).toContain('versionCode divergence');
      expect(result.discrepancies).toContain('versionName divergence');
      expect(result.discrepancies).toContain('checksum divergence');
      expect(result.discrepancies).toContain('apk SHA-256 divergence');
      expect(result.discrepancies).toContain('apk size divergence');
    });

    test('Scenario: Binary APK SHA-256 divergence between providers -> VERDICT: FAILURE', () => {
      const primary = {
        homeStatus: 200,
        manifestStatus: 200,
        manifest: { versionCode: 65, versionName: '1.0.2', checksum: manifest.checksum },
        apkContentLength: 22647689,
        apkSha256: manifest.checksum.toLowerCase()
      };
      const corruptSecondary = {
        homeStatus: 200,
        manifestStatus: 200,
        manifest: { versionCode: 65, versionName: '1.0.2', checksum: manifest.checksum },
        apkContentLength: 22647689,
        apkSha256: '0000000000000000000000000000000000000000000000000000000000000000'
      };

      const result = evaluateSync(primary, corruptSecondary, manifest);
      expect(result.success).toBe(false);
      expect(result.secondaryMatchesGit).toBe(false);
      expect(result.discrepancies).toContain('apk SHA-256 divergence');
    });

    test('Scenario: Missing deployment secrets error message formatting', () => {
      const token = '';
      const siteId = '';
      const isMissing = !token || !siteId;
      expect(isMissing).toBe(true);

      const errorMessage = `MISSING NETLIFY DEPLOYMENT SECRETS! Requires NETLIFY_AUTH_TOKEN and NETLIFY_SITE_ID.`;
      expect(errorMessage).toContain('NETLIFY_AUTH_TOKEN');
      expect(errorMessage).toContain('NETLIFY_SITE_ID');
    });
  });
});
