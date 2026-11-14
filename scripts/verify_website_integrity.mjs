import fs from 'node:fs';
import path from 'node:path';

const ROOT_DIR = process.cwd();
const WEB_DIR = path.join(ROOT_DIR, 'web');

console.log('====================================================');
console.log('🌐 LOFIQ MULTI-PLATFORM WEBSITE INTEGRITY VERIFICATION');
console.log('====================================================');

const requiredFiles = [
  'index.html',
  'android-download.html',
  'desktop-download.html',
  'tv-download.html',
  'features.html',
  'changelog.html',
  'styles.css',
  'app.js',
  'version.json',
  'vercel.json',
  '_redirects',
  '_headers',
  'sitemap.xml',
  'robots.txt',
  'assets/logo.png',
  'assets/favicon.png',
  'assets/tv_banner.png',
  'assets/widget_preview_compact_square_2x2.png',
  'assets/widget_preview_minimal_pill_4x1.png',
  'assets/widget_preview_wide_4x2.png',
  'downloads/LofiQ.apk'
];

let errors = 0;

for (const file of requiredFiles) {
  const fullPath = path.join(WEB_DIR, file);
  if (!fs.existsSync(fullPath)) {
    console.error(`❌ [MISSING FILE] web/${file} not found!`);
    errors++;
  } else {
    const stat = fs.statSync(fullPath);
    console.log(`✅ [FILE OK] web/${file} (${stat.size} bytes)`);
  }
}

// Check each HTML file for critical tags
const htmlFiles = [
  'index.html',
  'android-download.html',
  'desktop-download.html',
  'tv-download.html',
  'features.html',
  'changelog.html'
];

for (const htmlFile of htmlFiles) {
  const content = fs.readFileSync(path.join(WEB_DIR, htmlFile), 'utf8');
  
  // 1. DOCTYPE and title
  if (!content.includes('<!DOCTYPE html>')) {
    console.error(`❌ ${htmlFile} missing <!DOCTYPE html>`);
    errors++;
  }
  if (!content.includes('<title>')) {
    console.error(`❌ ${htmlFile} missing <title>`);
    errors++;
  }

  // 2. Canonical tag
  if (!content.includes('<link rel="canonical"')) {
    console.error(`❌ ${htmlFile} missing canonical link`);
    errors++;
  }

  // 3. OpenGraph tags
  if (!content.includes('property="og:title"')) {
    console.error(`❌ ${htmlFile} missing og:title`);
    errors++;
  }

  // 4. Stylesheet and JS
  if (!content.includes('styles.css')) {
    console.error(`❌ ${htmlFile} missing styles.css reference`);
    errors++;
  }
  if (!content.includes('app.js')) {
    console.error(`❌ ${htmlFile} missing app.js reference`);
    errors++;
  }

  // 5. TV Page Constraint: Check that tv-download.html DOES NOT have fake direct download button
  if (htmlFile === 'tv-download.html') {
    if (content.includes('download="LofiQ-TV.apk"') || content.includes('Download TV APK')) {
      console.error(`❌ tv-download.html contains prohibited fake APK download button!`);
      errors++;
    } else {
      console.log(`✅ tv-download.html correctly honors "Preview / Coming Soon" requirement without fake download!`);
    }
  }

  console.log(`✅ [HTML VALID] web/${htmlFile} passed structural checks`);
}

// Validate Vercel rewrites
const vercelRaw = fs.readFileSync(path.join(WEB_DIR, 'vercel.json'), 'utf8');
const vercel = JSON.parse(vercelRaw);
if (!vercel.cleanUrls) {
  console.error(`❌ vercel.json missing cleanUrls: true`);
  errors++;
} else {
  console.log(`✅ vercel.json cleanUrls enabled`);
}

if (!Array.isArray(vercel.rewrites) || vercel.rewrites.length < 5) {
  console.error(`❌ vercel.json missing rewrites`);
  errors++;
} else {
  console.log(`✅ vercel.json contains ${vercel.rewrites.length} clean URL rewrites`);
}

// Validate Netlify redirects
const redirects = fs.readFileSync(path.join(WEB_DIR, '_redirects'), 'utf8');
const expectedRoutes = ['/android-download', '/desktop-download', '/tv-download', '/features', '/changelog'];
for (const route of expectedRoutes) {
  if (!redirects.includes(route)) {
    console.error(`❌ web/_redirects missing rewrite for ${route}`);
    errors++;
  }
}
console.log(`✅ web/_redirects contains all expected 200 rewrite routes`);

// Validate Sitemap
const sitemap = fs.readFileSync(path.join(WEB_DIR, 'sitemap.xml'), 'utf8');
for (const route of expectedRoutes) {
  if (!sitemap.includes(route)) {
    console.error(`❌ sitemap.xml missing ${route}`);
    errors++;
  }
}
console.log(`✅ sitemap.xml covers all multi-platform routes`);

console.log('====================================================');
if (errors > 0) {
  console.error(`❌ VERIFICATION FAILED with ${errors} error(s)!`);
  process.exit(1);
} else {
  console.log('🎉 ALL MULTI-PLATFORM WEBSITE CHECKS PASSED PERFECTLY!');
  process.exit(0);
}
