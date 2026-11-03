document.addEventListener('DOMContentLoaded', async () => {
  /* =========================================================================
     1. Dynamic Version & Metadata Loading (from version.json)
     ========================================================================= */
  try {
    const res = await fetch('/version.json');
    if (res.ok) {
      const data = await res.json();
      
      // Update Version Badges
      const versionElements = document.querySelectorAll('.dynamic-version');
      versionElements.forEach(el => el.textContent = `v${data.versionName}`);

      const dateElements = document.querySelectorAll('.dynamic-date');
      dateElements.forEach(el => el.textContent = data.releaseDate || 'October 2026');

      const sizeElements = document.querySelectorAll('.dynamic-size');
      sizeElements.forEach(el => el.textContent = data.apkSize || '31.0 MB');

      // Update Download Links
      if (data.apkUrl) {
        const downloadButtons = document.querySelectorAll('.dynamic-download-btn');
        downloadButtons.forEach(btn => {
          btn.setAttribute('href', data.apkUrl);
          btn.setAttribute('download', `LofiQ-v${data.versionName}.apk`);
        });
      }

      // Dynamic Checksum
      if (data.checksum) {
        const csEl = document.getElementById('dynamic-checksum');
        if (csEl) csEl.textContent = data.checksum;
      }

      // Update Changelog Items
      if (Array.isArray(data.releaseNotes) && data.releaseNotes.length > 0) {
        const list = document.getElementById('changelog-items');
        if (list) {
          list.innerHTML = '';
          data.releaseNotes.slice(0, 7).forEach(note => {
            const li = document.createElement('li');
            li.innerHTML = `<span class="bullet">✦</span> ${note}`;
            list.appendChild(li);
          });
        }
      }
    }
  } catch (err) {
    console.warn('Could not load dynamic version.json:', err);
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
  /* =========================================================================
     5. Dual-Site Mirror Detection & Failover Handling
     ========================================================================= */
  const isNetlify = window.location.hostname.includes('netlify.app');
  const mirrorBadge = document.getElementById('mirror-badge');
  const backupMirrorBtn = document.getElementById('backup-mirror-btn');
  const backupMirrorBtnText = document.getElementById('backup-mirror-btn-text');
  const footerPrimaryLink = document.getElementById('footer-primary-link');
  const footerBackupLink = document.getElementById('footer-backup-link');

  if (isNetlify) {
    if (mirrorBadge) {
      mirrorBadge.innerHTML = '🛡️ <strong>Official Backup Mirror (Netlify)</strong> • <a href="https://lofiq-web.vercel.app">Switch to Primary (Vercel)</a>';
      mirrorBadge.style.borderColor = 'rgba(168, 85, 247, 0.4)';
      mirrorBadge.style.color = '#c084fc';
    }
    if (backupMirrorBtn) {
      backupMirrorBtn.href = 'https://lofiq-web.vercel.app/downloads/LofiQ.apk';
      if (backupMirrorBtnText) backupMirrorBtnText.textContent = 'Download via Primary Node (Vercel)';
    }
    if (footerBackupLink) {
      footerBackupLink.innerHTML = '<strong>Backup Mirror (Current)</strong>';
    }
  } else {
    if (footerPrimaryLink) {
      footerPrimaryLink.innerHTML = '<strong>Primary Node (Current)</strong>';
    }
  }
});
