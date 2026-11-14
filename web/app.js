/**
 * LOFIQ — Multi-Platform Commercial Experience & Metadata Hydration Engine
 * Supports Android, Windows Desktop, and Smart TV pages.
 */

document.addEventListener('DOMContentLoaded', async () => {
  /* =========================================================================
     1. Unified Dynamic Version & Metadata Loading (from /version.json)
     ========================================================================= */
  let versionManifest = null;

  try {
    const res = await fetch('/version.json');
    if (res.ok) {
      versionManifest = await res.json();
      const data = versionManifest;

      // Extract Android Metadata
      const androidVersion = data.android?.versionName || data.versionName || '1.0.6';
      const androidCode = data.android?.versionCode || data.versionCode || 69;
      const androidSize = data.android?.apkSize || data.apkSize || '21.8 MB';
      const androidDate = data.android?.releaseDate || data.releaseDate || '2026-11-14';
      const androidApkUrl = data.android?.apkUrl || data.apkUrl || '/downloads/LofiQ.apk';
      const androidChecksum = data.android?.checksum || data.checksum || '7ea4f8fc130160ae1bd2b468c1d5f1ab6626767551c539bc795b0336fa1612bb';

      // Update Android DOM Elements
      document.querySelectorAll('.dynamic-version').forEach(el => el.textContent = `v${androidVersion}`);
      document.querySelectorAll('.dynamic-version-raw').forEach(el => el.textContent = androidVersion);
      document.querySelectorAll('.dynamic-build-code').forEach(el => el.textContent = `Build ${androidCode}`);
      document.querySelectorAll('.dynamic-date').forEach(el => el.textContent = androidDate);
      document.querySelectorAll('.dynamic-size').forEach(el => el.textContent = androidSize);

      // Update Android Download Buttons
      document.querySelectorAll('.dynamic-download-btn').forEach(btn => {
        btn.setAttribute('href', androidApkUrl);
        btn.setAttribute('download', `LofiQ-v${androidVersion}.apk`);
      });

      // Update Android Checksum Elements
      const csEl = document.getElementById('dynamic-checksum');
      if (csEl) csEl.textContent = androidChecksum;
      document.querySelectorAll('.dynamic-checksum-text').forEach(el => el.textContent = androidChecksum);

      // Extract & Update Windows Metadata
      if (data.windows) {
        const win = data.windows;
        const winVer = win.versionName || '1.0.4';
        const winCode = win.versionCode || 67;
        const winInstallerSize = win.installerSize || '16.7 MB';
        const winPortableSize = win.portableSize || '19.7 MB';
        const winInstallerUrl = win.installerUrl || 'https://github.com/itsafxal/LofiQ-Music/releases/download/v1.0.4/LofiQ-v1.0.4-Windows-Setup.exe';
        const winPortableUrl = win.portableUrl || 'https://github.com/itsafxal/LofiQ-Music/releases/download/v1.0.4/LofiQ-v1.0.4-Windows-Portable.zip';
        const winChecksumUrl = win.checksumUrl || 'https://github.com/itsafxal/LofiQ-Music/releases/download/v1.0.4/SHA256SUMS.txt';
        const winInstallerCs = win.installerChecksum || '50eabc47418f505a226fe9322f967f6e753456b52868b4f5442a165b6da277e7';
        const winPortableCs = win.portableChecksum || 'a26602ee8cb258e7b9e5f9b707885a257e068cabfc151b677847bbdbed991ad8';

        document.querySelectorAll('.dynamic-windows-version').forEach(el => el.textContent = `v${winVer}`);
        document.querySelectorAll('.dynamic-windows-build').forEach(el => el.textContent = `Build ${winCode}`);
        document.querySelectorAll('.dynamic-win-installer-size').forEach(el => el.textContent = winInstallerSize);
        document.querySelectorAll('.dynamic-win-portable-size').forEach(el => el.textContent = winPortableSize);

        const winInstallerBtn = document.getElementById('win-installer-btn');
        if (winInstallerBtn && winInstallerUrl) {
          winInstallerBtn.setAttribute('href', winInstallerUrl);
        }

        const winPortableBtn = document.getElementById('win-portable-btn');
        if (winPortableBtn && winPortableUrl) {
          winPortableBtn.setAttribute('href', winPortableUrl);
        }

        const winChecksumLink = document.getElementById('win-checksum-link');
        if (winChecksumLink && winChecksumUrl) {
          winChecksumLink.setAttribute('href', winChecksumUrl);
        }

        const winInstallerCsEl = document.getElementById('win-installer-checksum');
        if (winInstallerCsEl) winInstallerCsEl.textContent = winInstallerCs;

        const winPortableCsEl = document.getElementById('win-portable-checksum');
        if (winPortableCsEl) winPortableCsEl.textContent = winPortableCs;
      }

      // Initial Changelog Rendering
      renderChangelog('android');
    }
  } catch (err) {
    console.warn('Could not load dynamic version.json metadata:', err);
  }

  function renderChangelog(platform) {
    if (!versionManifest) return;
    const list = document.getElementById('changelog-items');
    if (!list) return;

    let notes = [];
    if (platform === 'windows' && versionManifest.windows?.releaseNotes) {
      notes = versionManifest.windows.releaseNotes;
    } else {
      notes = versionManifest.android?.releaseNotes || versionManifest.releaseNotes || [];
    }

    list.innerHTML = '';
    notes.forEach(note => {
      const li = document.createElement('li');
      li.innerHTML = `<span class="bullet">&#10022;</span> ${note}`;
      list.appendChild(li);
    });
  }

  /* =========================================================================
     2. Multi-Platform 3-Way Selector (Android / Windows / Smart TV)
     ========================================================================= */
  const tabAndroid = document.getElementById('tab-btn-android');
  const tabWindows = document.getElementById('tab-btn-windows');
  const tabTv = document.getElementById('tab-btn-tv');

  const deckAndroid = document.getElementById('platform-deck-android');
  const deckWindows = document.getElementById('platform-deck-windows');
  const deckTv = document.getElementById('platform-deck-tv');

  const clTabAndroid = document.getElementById('cl-tab-android');
  const clTabWindows = document.getElementById('cl-tab-windows');

  function switchPlatform(platform) {
    // Reset all tabs
    [tabAndroid, tabWindows, tabTv].forEach(tab => {
      if (!tab) return;
      tab.classList.remove('active');
      tab.setAttribute('aria-selected', 'false');
    });

    // Reset all decks
    [deckAndroid, deckWindows, deckTv].forEach(deck => {
      if (!deck) return;
      deck.classList.remove('is-active');
    });

    if (platform === 'windows') {
      tabWindows?.classList.add('active');
      tabWindows?.setAttribute('aria-selected', 'true');
      deckWindows?.classList.add('is-active');

      clTabWindows?.classList.add('active');
      clTabAndroid?.classList.remove('active');
      renderChangelog('windows');
    } else if (platform === 'tv') {
      tabTv?.classList.add('active');
      tabTv?.setAttribute('aria-selected', 'true');
      deckTv?.classList.add('is-active');

      clTabAndroid?.classList.add('active');
      clTabWindows?.classList.remove('active');
      renderChangelog('android');
    } else {
      // Default to android
      tabAndroid?.classList.add('active');
      tabAndroid?.setAttribute('aria-selected', 'true');
      deckAndroid?.classList.add('is-active');

      clTabAndroid?.classList.add('active');
      clTabWindows?.classList.remove('active');
      renderChangelog('android');
    }
  }

  tabAndroid?.addEventListener('click', () => switchPlatform('android'));
  tabWindows?.addEventListener('click', () => switchPlatform('windows'));
  tabTv?.addEventListener('click', () => switchPlatform('tv'));

  clTabAndroid?.addEventListener('click', () => {
    clTabAndroid.classList.add('active');
    clTabWindows?.classList.remove('active');
    renderChangelog('android');
  });

  clTabWindows?.addEventListener('click', () => {
    clTabWindows.classList.add('active');
    clTabAndroid?.classList.remove('active');
    renderChangelog('windows');
  });

  // Hero Jump buttons
  document.getElementById('hero-jump-android')?.addEventListener('click', (e) => {
    e.preventDefault();
    switchPlatform('android');
    document.getElementById('platforms')?.scrollIntoView({ behavior: 'smooth' });
  });

  document.getElementById('hero-jump-windows')?.addEventListener('click', (e) => {
    e.preventDefault();
    switchPlatform('windows');
    document.getElementById('platforms')?.scrollIntoView({ behavior: 'smooth' });
  });

  document.getElementById('hero-jump-tv')?.addEventListener('click', (e) => {
    e.preventDefault();
    switchPlatform('tv');
    document.getElementById('platforms')?.scrollIntoView({ behavior: 'smooth' });
  });

  // Footer link hooks
  document.getElementById('footer-android-link')?.addEventListener('click', () => switchPlatform('android'));
  document.getElementById('footer-win-link')?.addEventListener('click', () => switchPlatform('windows'));
  document.getElementById('footer-tv-link')?.addEventListener('click', () => switchPlatform('tv'));

  // Hash-based automatic tab activation on page load
  const currentHash = window.location.hash.toLowerCase();
  if (currentHash === '#windows' || currentHash === '#desktop') {
    switchPlatform('windows');
  } else if (currentHash === '#tv' || currentHash === '#television') {
    switchPlatform('tv');
  } else if (currentHash === '#android') {
    switchPlatform('android');
  } else {
    // Intelligent platform check
    const isWindowsClient = navigator.userAgent.includes('Windows') || (navigator.platform && navigator.platform.includes('Win'));
    const isSmartTvClient = /Android TV|SmartTV|GoogleTV|HbbTV|NetCast|POV_TV/i.test(navigator.userAgent);
    
    if (isSmartTvClient) {
      switchPlatform('tv');
    } else if (isWindowsClient && deckWindows) {
      switchPlatform('windows');
    } else {
      switchPlatform('android');
    }
  }

  /* =========================================================================
     3. Mobile Navigation Drawer Toggle
     ========================================================================= */
  const mobileMenuBtn = document.getElementById('mobile-menu-btn');
  const mobileNavDrawer = document.getElementById('mobile-nav-drawer');
  const drawerBackdrop = document.getElementById('drawer-backdrop');
  const drawerCloseBtn = document.getElementById('drawer-close-btn');

  function openDrawer() {
    mobileNavDrawer?.classList.add('is-open');
    drawerBackdrop?.classList.add('is-open');
    document.body.style.overflow = 'hidden';
  }

  function closeDrawer() {
    mobileNavDrawer?.classList.remove('is-open');
    drawerBackdrop?.classList.remove('is-open');
    document.body.style.overflow = '';
  }

  mobileMenuBtn?.addEventListener('click', openDrawer);
  drawerCloseBtn?.addEventListener('click', closeDrawer);
  drawerBackdrop?.addEventListener('click', closeDrawer);

  document.querySelectorAll('.drawer-nav-link').forEach(link => {
    link.addEventListener('click', closeDrawer);
  });

  /* =========================================================================
     4. Interactive Liquid Glass Audio Player Mockup
     ========================================================================= */
  const playBtn = document.getElementById('ctrl-play');
  const playIcon = document.getElementById('play-icon');
  const visualizer = document.getElementById('visualizer');
  const progressBar = document.getElementById('progress-bar');
  const progressHandle = document.getElementById('progress-handle');
  const seekbar = document.getElementById('seekbar');
  const timeCurrent = document.getElementById('time-current');
  const timeTotal = document.getElementById('time-total');
  const outputPill = document.getElementById('audio-output-pill');
  const outputText = document.getElementById('output-text');

  let isPlaying = true;
  let currentSeconds = 102; // 01:42
  const totalSeconds = 268;   // 04:28
  let playbackInterval = null;

  function formatTime(sec) {
    const m = Math.floor(sec / 60);
    const s = Math.floor(sec % 60);
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }

  function updateProgress() {
    const pct = (currentSeconds / totalSeconds) * 100;
    if (progressBar) progressBar.style.width = `${pct}%`;
    if (progressHandle) progressHandle.style.left = `${pct}%`;
    if (timeCurrent) timeCurrent.textContent = formatTime(currentSeconds);
  }

  function startPlaybackTimer() {
    if (playbackInterval) clearInterval(playbackInterval);
    playbackInterval = setInterval(() => {
      if (isPlaying) {
        currentSeconds++;
        if (currentSeconds >= totalSeconds) currentSeconds = 0;
        updateProgress();
      }
    }, 1000);
  }

  if (playBtn) {
    playBtn.addEventListener('click', () => {
      isPlaying = !isPlaying;
      if (isPlaying) {
        if (playIcon) playIcon.innerHTML = '<path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>';
        visualizer?.classList.remove('visualizer-paused');
        startPlaybackTimer();
      } else {
        if (playIcon) playIcon.innerHTML = '<path d="M8 5v14l11-7z"/>';
        visualizer?.classList.add('visualizer-paused');
        clearInterval(playbackInterval);
      }
    });

    if (playIcon) playIcon.innerHTML = '<path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>';
    startPlaybackTimer();
  }

  if (seekbar) {
    seekbar.addEventListener('click', (e) => {
      const rect = seekbar.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const pct = Math.max(0, Math.min(1, clickX / rect.width));
      currentSeconds = Math.floor(pct * totalSeconds);
      updateProgress();
    });
  }

  const tracks = [
    { title: 'Kesariya (From "Brahmastra")', artist: 'Arijit Singh, Pritam', duration: 268 },
    { title: 'Chaleya (From "Jawan")', artist: 'Anirudh Ravichander, Shilpa Rao', duration: 200 },
    { title: 'Illuminati (From "Aavesham")', artist: 'Sushin Shyam, Dabzee', duration: 193 },
    { title: 'Starboy (Lossless Studio Master)', artist: 'The Weeknd, Daft Punk', duration: 230 }
  ];
  let currentTrackIdx = 0;

  function switchTrack(idx) {
    currentTrackIdx = (idx + tracks.length) % tracks.length;
    const track = tracks[currentTrackIdx];
    const trackTitleEl = document.getElementById('track-title');
    const trackArtistEl = document.querySelector('.track-artist');
    if (trackTitleEl) trackTitleEl.textContent = track.title;
    if (trackArtistEl) trackArtistEl.textContent = track.artist;
    currentSeconds = 0;
    if (timeTotal) timeTotal.textContent = formatTime(track.duration);
    updateProgress();
  }

  document.getElementById('ctrl-next')?.addEventListener('click', () => switchTrack(currentTrackIdx + 1));
  document.getElementById('ctrl-prev')?.addEventListener('click', () => switchTrack(currentTrackIdx - 1));

  // Cycle Audio Output Route
  const outputRoutes = [
    'Output: Studio Device (320kbps)',
    'Output: Bluetooth LDAC (990kbps)',
    'Output: Wired Hi-Res DAC (Lossless)',
    'Output: Car Audio (Android Auto)'
  ];
  let currentRouteIdx = 0;

  if (outputPill) {
    outputPill.addEventListener('click', () => {
      currentRouteIdx = (currentRouteIdx + 1) % outputRoutes.length;
      if (outputText) {
        outputText.textContent = outputRoutes[currentRouteIdx];
        outputPill.style.transform = 'scale(0.96)';
        setTimeout(() => outputPill.style.transform = 'scale(1)', 150);
      }
    });
  }

  /* =========================================================================
     5. Interactive TV Remote Control Simulation
     ========================================================================= */
  const tvCards = document.querySelectorAll('.tv-card-preview');
  let currentTvCardIdx = 0;

  function updateTvFocus(index) {
    if (!tvCards || tvCards.length === 0) return;
    currentTvCardIdx = (index + tvCards.length) % tvCards.length;
    tvCards.forEach((card, idx) => {
      if (idx === currentTvCardIdx) {
        card.classList.add('is-focused');
      } else {
        card.classList.remove('is-focused');
      }
    });
  }

  document.getElementById('remote-left')?.addEventListener('click', () => updateTvFocus(currentTvCardIdx - 1));
  document.getElementById('remote-right')?.addEventListener('click', () => updateTvFocus(currentTvCardIdx + 1));
  document.getElementById('remote-up')?.addEventListener('click', () => updateTvFocus(currentTvCardIdx - 1));
  document.getElementById('remote-down')?.addEventListener('click', () => updateTvFocus(currentTvCardIdx + 1));
  
  document.getElementById('remote-ok')?.addEventListener('click', () => {
    const activeCard = tvCards[currentTvCardIdx];
    if (activeCard) {
      activeCard.style.boxShadow = '0 0 35px #ffffff, inset 0 0 20px rgba(255,255,255,0.8)';
      setTimeout(() => {
        activeCard.style.boxShadow = '';
      }, 300);
    }
  });

  /* =========================================================================
     6. TV Priority Notification Form Handler
     ========================================================================= */
  const tvNotifyForms = document.querySelectorAll('.notify-form');
  tvNotifyForms.forEach(form => {
    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const input = form.querySelector('.notify-input');
      const msg = form.parentElement.querySelector('.notify-success-msg');
      if (input && input.value) {
        try {
          localStorage.setItem('lofiq_tv_notify_email', input.value.trim());
        } catch (_) {}
        input.value = '';
        if (msg) {
          msg.style.display = 'block';
          msg.textContent = '✓ You are on the priority waitlist! We will notify you the moment the Smart TV build goes live.';
        }
      }
    });
  });

  /* =========================================================================
     7. 1-Click Checksum Copy Helper
     ========================================================================= */
  document.querySelectorAll('.copy-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const targetId = btn.getAttribute('data-copy-target');
      const targetEl = document.getElementById(targetId);
      const textToCopy = targetEl ? targetEl.textContent.trim() : btn.getAttribute('data-copy-text');

      if (textToCopy) {
        try {
          await navigator.clipboard.writeText(textToCopy);
          const origText = btn.innerHTML;
          btn.classList.add('copied');
          btn.innerHTML = '<span>✓ Copied!</span>';
          setTimeout(() => {
            btn.classList.remove('copied');
            btn.innerHTML = origText;
          }, 2000);
        } catch (e) {
          console.warn('Clipboard write error:', e);
        }
      }
    });
  });

  /* =========================================================================
     8. Mobile Sticky Floating Bottom Bar Scroll Detection
     ========================================================================= */
  const mobileDock = document.getElementById('mobile-dock');
  const heroSection = document.getElementById('hero');

  if (mobileDock && heroSection) {
    window.addEventListener('scroll', () => {
      const heroBottom = heroSection.getBoundingClientRect().bottom;
      if (heroBottom < 100) {
        mobileDock.classList.add('is-visible');
      } else {
        mobileDock.classList.remove('is-visible');
      }
    }, { passive: true });
  }

  /* =========================================================================
     9. Desktop 3D Tilt / Liquid Specular Light Reflection
     ========================================================================= */
  const cards = document.querySelectorAll('.feature-card, .phone-mockup, .desktop-monitor-mockup, .tv-frame, .download-container');
  
  if (window.matchMedia('(min-width: 1024px)').matches) {
    cards.forEach(card => {
      card.addEventListener('mousemove', (e) => {
        const rect = card.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        const centerX = rect.width / 2;
        const centerY = rect.height / 2;
        
        const rotateX = ((y - centerY) / centerY) * -2.5;
        const rotateY = ((x - centerX) / centerX) * 2.5;
        
        card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateY(-3px)`;
      });

      card.addEventListener('mouseleave', () => {
        card.style.transform = '';
      });
    });
  }
});
