document.addEventListener('DOMContentLoaded', async () => {
  /* =========================================================================
     1. Unified Android + Windows Dynamic Version & Metadata Loading
     ========================================================================= */
  let versionManifest = null;

  try {
    const res = await fetch('/version.json');
    if (res.ok) {
      versionManifest = await res.json();
      const data = versionManifest;
      
      // Update Android Version Badges & Metadata
      const androidVersion = data.android?.versionName || data.versionName || '1.0.2';
      const androidSize = data.android?.apkSize || data.apkSize || '21.7 MB';
      const androidDate = data.android?.releaseDate || data.releaseDate || 'November 2026';
      const androidApkUrl = data.android?.apkUrl || data.apkUrl || '/downloads/LofiQ.apk';
      const androidChecksum = data.android?.checksum || data.checksum || '';

      const versionElements = document.querySelectorAll('.dynamic-version');
      versionElements.forEach(el => el.textContent = `v${androidVersion}`);

      const dateElements = document.querySelectorAll('.dynamic-date');
      dateElements.forEach(el => el.textContent = androidDate);

      const sizeElements = document.querySelectorAll('.dynamic-size');
      sizeElements.forEach(el => el.textContent = androidSize);

      // Update Android Download Links
      const downloadButtons = document.querySelectorAll('.dynamic-download-btn');
      downloadButtons.forEach(btn => {
        btn.setAttribute('href', androidApkUrl);
        btn.setAttribute('download', `LofiQ-v${androidVersion}.apk`);
      });

      // Android Dynamic Checksum
      if (androidChecksum) {
        const csEl = document.getElementById('dynamic-checksum');
        if (csEl) csEl.textContent = androidChecksum;
      }

      // Update Windows Metadata
      if (data.windows) {
        const win = data.windows;
        const winVerElements = document.querySelectorAll('.dynamic-windows-version');
        winVerElements.forEach(el => el.textContent = `v${win.versionName || '1.0.2'}`);

        const winInstallerSizeEl = document.querySelector('.dynamic-win-installer-size');
        if (winInstallerSizeEl) winInstallerSizeEl.textContent = win.installerSize || '~25 MB';

        const winPortableSizeEl = document.querySelector('.dynamic-win-portable-size');
        if (winPortableSizeEl) winPortableSizeEl.textContent = win.portableSize || '~28 MB';

        const winInstallerBtn = document.getElementById('win-installer-btn');
        if (winInstallerBtn && win.installerUrl) {
          winInstallerBtn.setAttribute('href', win.installerUrl);
        }

        const winPortableBtn = document.getElementById('win-portable-btn');
        if (winPortableBtn && win.portableUrl) {
          winPortableBtn.setAttribute('href', win.portableUrl);
        }

        const winChecksumLink = document.getElementById('win-checksum-link');
        if (winChecksumLink && win.checksumUrl) {
          winChecksumLink.setAttribute('href', win.checksumUrl);
        }
      }

      // Render Changelog for initial platform
      renderChangelog('android');
    }
  } catch (err) {
    console.warn('Could not load dynamic version.json:', err);
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
    notes.slice(0, 7).forEach(note => {
      const li = document.createElement('li');
      li.innerHTML = `<span class="bullet">✦</span> ${note}`;
      list.appendChild(li);
    });
  }

  /* =========================================================================
     1.1 Platform Switcher (Android / Windows Tabs)
     ========================================================================= */
  const tabAndroid = document.getElementById('tab-btn-android');
  const tabWindows = document.getElementById('tab-btn-windows');
  const deckAndroid = document.getElementById('platform-deck-android');
  const deckWindows = document.getElementById('platform-deck-windows');
  const clTabAndroid = document.getElementById('cl-tab-android');
  const clTabWindows = document.getElementById('cl-tab-windows');
  const heroWinBtn = document.getElementById('hero-windows-download');
  const footerWinLink = document.getElementById('footer-win-link');

  function switchPlatform(platform) {
    if (platform === 'windows') {
      tabWindows?.classList.add('active');
      tabWindows?.setAttribute('aria-selected', 'true');
      tabAndroid?.classList.remove('active');
      tabAndroid?.setAttribute('aria-selected', 'false');

      deckWindows?.classList.add('is-active');
      deckAndroid?.classList.remove('is-active');

      clTabWindows?.classList.add('active');
      clTabAndroid?.classList.remove('active');
      renderChangelog('windows');
    } else {
      tabAndroid?.classList.add('active');
      tabAndroid?.setAttribute('aria-selected', 'true');
      tabWindows?.classList.remove('active');
      tabWindows?.setAttribute('aria-selected', 'false');

      deckAndroid?.classList.add('is-active');
      deckWindows?.classList.remove('is-active');

      clTabAndroid?.classList.add('active');
      clTabWindows?.classList.remove('active');
      renderChangelog('android');
    }
  }

  tabAndroid?.addEventListener('click', () => switchPlatform('android'));
  tabWindows?.addEventListener('click', () => switchPlatform('windows'));

  clTabAndroid?.addEventListener('click', () => {
    switchPlatform('android');
  });
  clTabWindows?.addEventListener('click', () => {
    switchPlatform('windows');
  });

  heroWinBtn?.addEventListener('click', () => {
    switchPlatform('windows');
  });

  footerWinLink?.addEventListener('click', () => {
    switchPlatform('windows');
  });

  // Automatic Platform Detection on Page Load
  const isWindowsHost = navigator.userAgent.includes('Windows') || (navigator.platform && navigator.platform.includes('Win'));
  if (isWindowsHost) {
    switchPlatform('windows');
  } else {
    switchPlatform('android');
  }

  /* =========================================================================
     2. Interactive Liquid Glass Audio Player Mockup
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
        // Switch to pause icon
        playIcon.innerHTML = '<path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>';
        visualizer?.classList.remove('visualizer-paused');
        startPlaybackTimer();
      } else {
        // Switch to play icon
        playIcon.innerHTML = '<path d="M8 5v14l11-7z"/>';
        visualizer?.classList.add('visualizer-paused');
        clearInterval(playbackInterval);
      }
    });

    // Initialize with pause icon (playing state)
    playIcon.innerHTML = '<path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>';
    startPlaybackTimer();
  }

  // Interactive Seekbar Scrubbing
  if (seekbar) {
    seekbar.addEventListener('click', (e) => {
      const rect = seekbar.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const pct = Math.max(0, Math.min(1, clickX / rect.width));
      currentSeconds = Math.floor(pct * totalSeconds);
      updateProgress();
    });
  }

  // Next / Prev track button simulation
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

  document.getElementById('ctrl-next')?.addEventListener('click', () => {
    switchTrack(currentTrackIdx + 1);
  });

  document.getElementById('ctrl-prev')?.addEventListener('click', () => {
    switchTrack(currentTrackIdx - 1);
  });

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
     3. Mobile Sticky Floating Bottom Bar Scroll Detection
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
     4. Desktop 3D Tilt / Liquid Specular Light Reflection
     ========================================================================= */
  const cards = document.querySelectorAll('.feature-card, .phone-mockup, .download-container');
  
  if (window.matchMedia('(min-width: 1024px)').matches) {
    cards.forEach(card => {
      card.addEventListener('mousemove', (e) => {
        const rect = card.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;
        
        const centerX = rect.width / 2;
        const centerY = rect.height / 2;
        
        const rotateX = ((y - centerY) / centerY) * -3;
        const rotateY = ((x - centerX) / centerX) * 3;
        
        card.style.transform = `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateY(-4px)`;
      });

      card.addEventListener('mouseleave', () => {
        card.style.transform = '';
      });
    });
  }
});
