# LOFIQ — Smart Music Queue Management & Reliable Home Tab Navigation
## Master Implementation Report

**Project:** LofiQ Music  
**Platform:** Android (Jetpack Compose + MVVM + Media3 / ExoPlayer PlaybackEngine)  
**Date:** November 14, 2026  
**Status:** Complete & Verified  

---

## 1. Executive Summary

This engineering initiative resolves two core architectural and UX challenges in the LofiQ Android application:

1. **Smart Music Queue Management with Universal Long-Press Song Context Menu:**
   - Designed and built a universal `SongContextMenuSheet` featuring LofiQ's signature **Liquid Glass** design language, haptic feedback, artwork presentation, and real-time download integration.
   - Wired long-press contextual menus across all song-display surfaces: Home shelves (carousel, compact grid, featured large), Search results, Search discovery/best match, Library/Collection details (playlists, albums, artist tracks), Album songs bottom sheet, and Local Music files.
   - Refactored `PlaybackEngine` queue operations into a strictly deterministic queue state machine:
     - **Play Next:** Immediate next track insertion (`currentIndex + 1`) following strict **LIFO (Last-In-First-Out) priority**.
     - **Add to Queue:** Upcoming queue tail insertion following **FIFO (First-In-First-Out)** ordering.
     - **Duplicate Song Support:** Eliminated duplicate key Compose crashes in `QueueSheet` by transitioning from raw song ID keys to instance-unique `QueueItem.id` (UUID) tokens.
2. **Reliable Bottom Navigation Home Tab Reset:**
   - Fixed the persistent navigation bug where navigating through deep routes (e.g. Settings, Equalizer, Profile, Library, Search, Album/Artist details) and tapping Home failed to return the user to the root Home screen.
   - Restructured `BottomNavBar` and `MainScreen` destination dispatching to guarantee that tapping Home unconditionally clears nested backstacks down to the root Home destination while preserving ongoing audio playback, MiniPlayer visibility, and the active queue.

All implementations were verified via **22 automated unit test cases** (12 for Queue Management, 10 for Navigation Reset) and clean Gradle compilation.

---

## 2. Architecture & Component Modifications

### 2.1 State Layer & Duplicate Item Key Architecture
- **File:** [PlaybackState.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/core/model/PlaybackState.kt)
  - Extended `LofiQPlayerState` to expose `val queueItems: List<QueueItem> = emptyList()`.
  - While `queue: List<Song>` maintains backward compatibility for legacy observers, `queueItems` equips every queue entry with a unique `QueueItem(id = UUID.randomUUID().toString(), song = song)`.
  - **Resolution of Key Collisions:** Previously, when a user added the same song to the queue multiple times, `QueueSheet`'s `LazyColumn` crashed with `IllegalArgumentException: Key was already used`. Keying on `QueueItem.id` guarantees that identical songs can exist across multiple queue slots safely.

### 2.2 PlaybackEngine Queue Engine Refactor
- **File:** [PlaybackEngine.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/playback/engine/PlaybackEngine.kt)
  - **Internal Data Structure:** Migrated internal `currentQueue` from `mutableListOf<Song>()` to `mutableListOf<QueueItem>()`.
  - **Elimination of `priorityQueueOffset`:** In the previous implementation, each call to `playNextInQueue` incremented an offset counter (`currentIndex + 1 + priorityQueueOffset`), which erroneously placed later "Play Next" songs *after* earlier ones instead of immediately next. This offset has been removed in favor of pure `currentIndex + 1` insertion.
  - **Updated Methods:**
    - `playSong(song)` / `playQueue(songs, initialIndex)`: Initializes queue with unique `QueueItem` instances.
    - `playQueueItem(queueItem)`: Resolves playback directly via instance reference.
    - `playNextInQueue(song)` / `enqueuePriorityNext(song)`: Strictly inserts at `currentIndex + 1`. If the queue is empty, initiates immediate playback.
    - `addToQueue(song)`: Appends at the tail (`currentQueue.add(QueueItem(song))`).
    - `reorderUpcomingQueueItems(newUpcomingItems)`: Slices and updates upcoming items in-place without disturbing `currentQueue[0..currentIndex]`.
    - `clearUpcomingQueue()`: Safely purges upcoming queue items without mutating the playing track.
    - `removeQueueItem(queueItemId)`: Removes by unique item id, adjusting `currentIndex` dynamically.
  - **Gapless & Crossfade Safety:** All prefetch and lookahead logic (`triggerUpcomingPrefetch`, `getNextSong`, `preloadNextTrack`, `handleTrackEnded`) seamlessly read from `currentQueue.getOrNull(currentIndex + 1)?.song`, ensuring zero disruption to crossfade or media pipeline preloading.

### 2.3 Universal Song Context Menu Sheet
- **File:** [SongContextMenuSheet.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/ui/components/SongContextMenuSheet.kt)
  - Built an accessible, gesture-dismissible `ModalBottomSheet` honoring LofiQ's Liquid Glass styling (acrylic gradient overlays, 16% surface alpha, dark border stroke, rounded header corners).
  - Integrates direct actions with `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`:
    1. **Play Next:** Calls `playbackEngine.playNextInQueue(song)` with confirmation toast.
    2. **Add to Queue:** Calls `playbackEngine.addToQueue(song)` with confirmation toast.
    3. **Download Integration:** Collects `trackDownloadManager.downloads` StateFlow dynamically. Displays live download progress percentages when in progress, "Downloaded" with checkmark when completed, and a retry state if failed. Tapping triggers `trackDownloadManager.downloadTrack(song)`.
  - Exposes `LocalSongContextMenu = compositionLocalOf<(Song) -> Unit>` for seamless injection across the Compose tree.

### 2.4 Universal Long-Press UI Wiring
The long-press gesture (`Modifier.combinedClickable(onClick = { ... }, onLongClick = { onLongClick(song) })`) was added to all song entry points:
1. [SongRow.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/ui/components/SongRow.kt): Reusable song row. Also wired the 3-dot `IconButton` to trigger the context menu.
2. [AlbumCard.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/ui/components/AlbumCard.kt): Added `onLongClick` support to `AlbumCard`, `CompactSongRowCard`, and `FeaturedLargeAlbumCard`.
3. [HomeScreen.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/feature/home/HomeScreen.kt): Wired `onLongClick` handlers for Carousel Shelf, Compact Grid Shelf, and Featured Large Shelf.
4. [SearchScreen.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/feature/search/SearchScreen.kt): Wired `combinedClickable` on `BestMatchCard` and search result rows.
5. [CollectionDetailScreen.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/ui/components/CollectionDetailScreen.kt): Wired track list items and 3-dot buttons in playlist/album/artist detail views.
6. [AlbumSongsSheet.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/feature/player/AlbumSongsSheet.kt): Wired songs in the player's album drawer.
7. [LocalMusicScreen.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/feature/local/LocalMusicScreen.kt): Wired local music tracks.

### 2.5 Queue Sheet Synchronization
- **File:** [QueueSheet.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/feature/queue/QueueSheet.kt)
  - Updated `itemsIndexed(items = upcomingItems, key = { _, item -> item.id })` to bind directly to `QueueItem.id`.
  - Reorder callbacks now pass `reorderUpcomingQueueItems(localUpcomingItems)` directly to `PlaybackEngine`, maintaining smooth drag-and-drop reordering without index corruption.

---

## 3. Bottom Navigation Home Reset Fix

### 3.1 Problem Analysis
In the original implementation:
1. `BottomNavBar.kt` guarded navigation clicks with `if (!isSelected) { onTabSelected(screen) }`. When the user was viewing a nested destination that was considered part of the current tab (or if the tab was re-clicked), the click event was completely ignored.
2. In `MainScreen.kt`, navigation was configured with:
   ```kotlin
   popUpTo(navController.graph.findStartDestination().id) {
       saveState = true
   }
   launchSingleTop = true
   restoreState = true
   ```
   When navigating away from Home to Settings and then tapping Home, the navigation backstack restored the saved backstack state containing the nested screen on top of Home.
3. The bottom bar was conditionally hidden on non-root routes (`isBottomBarRoute = currentRoute in topLevelRoutes`), trapping users on screens like Settings, Equalizer, Profile, or Local Music without direct bottom navigation access.

### 3.2 Solution Architecture
- **File:** [BottomNavBar.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/ui/navigation/BottomNavBar.kt)
  - Removed `if (!isSelected)` gate. All tab clicks are delivered to the navigation handler, allowing re-selection to act as a reset command.
- **File:** [MainScreen.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/ui/navigation/MainScreen.kt)
  - **Persistent Navigation Chrome:** Changed `isBottomBarRoute = !isFullPlayerVisible`, ensuring the bottom navigation bar and MiniPlayer stay accessible across nested pages.
  - **Deterministic Home Reset:**
    ```kotlin
    if (screen == NavScreen.HOME) {
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = false
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
        navController.popBackStack(NavScreen.HOME.route, inclusive = false)
    } else {
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    ```
  - **Guarantees:**
    - Tapping Home unconditionally clears all stacked routes above Home without saving state.
    - Ongoing playback, ExoPlayer instance, active audio service, MiniPlayer, and the playback queue are entirely decoupled from NavHost destination recreation and remain 100% uninterrupted.

---

## 4. Deterministic Queue Logic & Mathematical Proof

### 4.1 Specification Walkthrough
Given:
- **Currently playing:** Song `A` (`currentIndex = 0`)
- **Initial queue:** `[A]`

User actions performed in sequence:
1. Long press Song `B` → **Play Next**
   - Insert at `currentIndex + 1` (index 1): `[A, B]`
2. Long press Song `C` → **Play Next**
   - Insert at `currentIndex + 1` (index 1): `[A, C, B]`
3. Long press Song `D` → **Play Next**
   - Insert at `currentIndex + 1` (index 1): `[A, D, C, B]`
4. Long press Song `E` → **Add to Queue**
   - Append at tail: `[A, D, C, B, E]`
5. Long press Song `F` → **Add to Queue**
   - Append at tail: `[A, D, C, B, E, F]`

### 4.2 State Table

| Position | Song | Insertion Method | Priority Class |
|:---:|:---:|:---|:---|
| **0** | **A** | Active Track | Playing (`currentIndex = 0`) |
| **1** | **D** | Play Next (3rd action) | LIFO Priority 1 |
| **2** | **C** | Play Next (2nd action) | LIFO Priority 2 |
| **3** | **B** | Play Next (1st action) | LIFO Priority 3 |
| **4** | **E** | Add to Queue (4th action) | FIFO Tail 1 |
| **5** | **F** | Add to Queue (5th action) | FIFO Tail 2 |

**Final Verification:** The upcoming playback order is strictly `[D, C, B, E, F]`.

---

## 5. Automated Unit Test Verification

Two comprehensive test suites were created and executed with Android Gradle:

### 5.1 Smart Queue Management Test Suite
- **File:** [SmartQueueManagementTest.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/test/java/com/lofiq/core/SmartQueueManagementTest.kt)

| # | Test Method Name | Specification Verified | Status |
|---|---|---|:---:|
| 1 | `test_01_addOneSongToQueue()` | Append single song to queue end | **PASSED** |
| 2 | `test_02_addMultipleSongsToQueue()` | Append multiple songs to queue end in order | **PASSED** |
| 3 | `test_03_playNextInsertsAfterCurrentSong()` | Play Next places song immediately at `currentIndex + 1` | **PASSED** |
| 4 | `test_04_multiplePlayNextActionsFollowLifoPriorityOrdering()` | Walkthrough proof (`A -> B -> C -> D -> E -> F` = `[A, D, C, B, E, F]`) | **PASSED** |
| 5 | `test_05_addToQueuePreservesFifoOrdering()` | Repeated Add to Queue preserves FIFO order at tail | **PASSED** |
| 6 | `test_06_existingUpcomingSongsRemainIntact()` | Existing upcoming songs shift correctly without loss | **PASSED** |
| 7 | `test_07_duplicateSongsCanExistAsSeparateQueueEntries()` | Duplicate songs have unique `QueueItem.id` instances | **PASSED** |
| 8 | `test_08_addingSongsDoesNotInterruptCurrentPlayback()` | Current song and index are unaltered by queue insertions | **PASSED** |
| 9 | `test_09_queueIndexRemainsValidAfterInsertion()` | Queue bounds and index integrity across operations | **PASSED** |
| 10 | `test_10_dragAndDropReorderingWorks()` | Upward and downward upcoming drag-and-drop reordering | **PASSED** |
| 11 | `test_11_shuffleAndRepeatBehaviorRemainsCorrect()` | Queue navigation under repeat/shuffle states | **PASSED** |
| 12 | `test_12_queueUpdatesReflectedInPlayerState()` | `LofiQPlayerState.queue` & `queueItems` are synchronized | **PASSED** |

### 5.2 Home Navigation Reset Test Suite
- **File:** [HomeNavigationTest.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/test/java/com/lofiq/ui/navigation/HomeNavigationTest.kt)

| # | Test Method Name | Specification Verified | Status |
|---|---|---|:---:|
| 1 | `test_01_settingsToHome()` | Navigating from Settings to Home lands on Home root | **PASSED** |
| 2 | `test_02_equalizerToHome()` | Navigating from Equalizer to Home lands on Home root | **PASSED** |
| 3 | `test_03_libraryToHome()` | Navigating from Library to Home lands on Home root | **PASSED** |
| 4 | `test_04_searchToHome()` | Navigating from Search to Home lands on Home root | **PASSED** |
| 5 | `test_05_profileToHome()` | Navigating from Profile to Home lands on Home root | **PASSED** |
| 6 | `test_06_nestedScreenToHome()` | Deep nested routes (Home -> Album -> Artist -> Home) reset to Home | **PASSED** |
| 7 | `test_07_repeatedHomeTapsDoNotCreateDuplicateDestinations()` | Multiple consecutive Home taps enforce `launchSingleTop` | **PASSED** |
| 8 | `test_08_playbackContinuesDuringNavigation()` | Playback state, track, and position survive navigation | **PASSED** |
| 9 | `test_09_queueStateRemainsUnchanged()` | Active queue is preserved during navigation stack reset | **PASSED** |
| 10 | `test_10_homeRootContentIsDisplayedAfterNavigation()` | Stack depth is exactly 1 with `NavScreen.HOME` on top | **PASSED** |

### 5.3 Combined Test Execution Summary
```
> Task :app:testDebugUnitTest

com.lofiq.core.SmartQueueManagementTest > test_01_addOneSongToQueue PASSED
com.lofiq.core.SmartQueueManagementTest > test_02_addMultipleSongsToQueue PASSED
com.lofiq.core.SmartQueueManagementTest > test_03_playNextInsertsAfterCurrentSong PASSED
com.lofiq.core.SmartQueueManagementTest > test_04_multiplePlayNextActionsFollowLifoPriorityOrdering PASSED
com.lofiq.core.SmartQueueManagementTest > test_05_addToQueuePreservesFifoOrdering PASSED
com.lofiq.core.SmartQueueManagementTest > test_06_existingUpcomingSongsRemainIntact PASSED
com.lofiq.core.SmartQueueManagementTest > test_07_duplicateSongsCanExistAsSeparateQueueEntries PASSED
com.lofiq.core.SmartQueueManagementTest > test_08_addingSongsDoesNotInterruptCurrentPlayback PASSED
com.lofiq.core.SmartQueueManagementTest > test_09_queueIndexRemainsValidAfterInsertion PASSED
com.lofiq.core.SmartQueueManagementTest > test_10_dragAndDropReorderingWorks PASSED
com.lofiq.core.SmartQueueManagementTest > test_11_shuffleAndRepeatBehaviorRemainsCorrect PASSED
com.lofiq.core.SmartQueueManagementTest > test_12_queueUpdatesReflectedInPlayerState PASSED

com.lofiq.ui.navigation.HomeNavigationTest > test_01_settingsToHome PASSED
com.lofiq.ui.navigation.HomeNavigationTest > test_02_equalizerToHome PASSED
com.lofiq.ui.navigation.HomeNavigationTest > test_03_libraryToHome PASSED
com.lofiq.ui.navigation.HomeNavigationTest > test_04_searchToHome PASSED
com.lofiq.ui.navigation.HomeNavigationTest > test_05_profileToHome PASSED
com.lofiq.ui.navigation.HomeNavigationTest > test_06_nestedScreenToHome PASSED
com.lofiq.ui.navigation.HomeNavigationTest > test_07_repeatedHomeTapsDoNotCreateDuplicateDestinations PASSED
com.lofiq.ui.navigation.HomeNavigationTest > test_08_playbackContinuesDuringNavigation PASSED
com.lofiq.ui.navigation.HomeNavigationTest > test_09_queueStateRemainsUnchanged PASSED
com.lofiq.ui.navigation.HomeNavigationTest > test_10_homeRootContentIsDisplayedAfterNavigation PASSED

BUILD SUCCESSFUL in 8s
22 tests completed, 0 failed, 0 skipped.
```

---

## 7. Master Addendum — Player Queue Gesture & Download Progress Notification

### 7.1 Removal of Player Queue (Q) Button & Gesture-Driven QueueSheet
- **File:** [FullPlayerSheet.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/feature/player/FullPlayerSheet.kt)
  - **Complete Removal of Visible Q Button:** Removed the visible pill container with the "Q" badge, "Queue" text, and chevron icon from the player bottom chrome.
  - **Gesture-Driven Access:** Implemented an invisible touch interaction area at the bottom of the player (`pointerInput` detecting vertical drag `<= -25f`) alongside the full-canvas upward drag (`totalDragY < -45f`), both seamlessly setting `showQueueSheet = true`.
  - **Gesture Isolation:** Horizontal scrubbing on `CustomPlayerSeekbar` and horizontal track skipping (`abs(dragX) > abs(dragY)`) consume horizontal touch, ensuring seeking and skipping never accidentally trigger queue expansion.
  - **Smooth Animations:** Retained native `ModalBottomSheetState` spring animations for smooth expand and dismiss transitions.

### 7.2 Native Android Download Progress Notifications
- **File:** [DownloadNotificationHelper.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/data/download/DownloadNotificationHelper.kt)
- **File:** [TrackDownloadManager.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/main/java/com/lofiq/data/download/TrackDownloadManager.kt)
  - **Notification Channel:** Registered `lofiq_track_downloads` ("Song Downloads") at `NotificationManager.IMPORTANCE_LOW` without vibration or sounds, guaranteeing zero interference with ongoing music playback.
  - **Live Dynamic Progress:** Displays the small download icon (`android.R.drawable.stat_sys_download`), track title, and live percentage (`0%..100%`) reflecting actual downloaded bytes and metadata embedding.
  - **Update Throttling:** Debounces progress updates (minimum 2% increment or 250ms interval) to protect `NotificationManager` from rate-limiting.
  - **Multiple Concurrent Downloads:** Stable notification IDs partitioned per `song.id` prevent concurrent downloads from overwriting each other.
  - **Automatic Cleanup:** Immediately calls `notificationManager.cancel(notificationId)` upon download completion, failure, or cancellation. Leaves zero lingering notifications or completed-download action buttons.
  - **Download Library Preservation:** Completed tracks are saved to device public storage (`Music/LofiQ`) or private app storage and indexed in Room `SongDao`.

---

## 8. Addendum Automated Test Verification

Two additional automated unit test suites were implemented:

### 8.1 Player Queue Gesture Test Suite
- **File:** [PlayerQueueGestureTest.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/test/java/com/lofiq/feature/player/PlayerQueueGestureTest.kt)

| # | Test Method Name | Specification Verified | Status |
|---|---|---|:---:|
| 1 | `test_01_horizontalSwipeDoesNotTriggerQueueSheet()` | Horizontal drags do not trigger QueueSheet | **PASSED** |
| 2 | `test_02_verticalSwipeUpOpensQueueSheet()` | Player vertical swipe-up opens QueueSheet | **PASSED** |
| 3 | `test_03_bottomInteractionAreaSwipeUpOpensQueueSheet()` | Bottom edge swipe-up opens QueueSheet | **PASSED** |
| 4 | `test_04_verticalSwipeDownDismissesPlayer()` | Downward swipe dismisses FullPlayer | **PASSED** |
| 5 | `test_05_seekingGestureIsolation()` | Seekbar scrub gestures do not open Queue | **PASSED** |
| 6 | `test_06_queueOperationsIntactWithoutQButton()` | Queue operations intact without visible Q button | **PASSED** |
| 7 | `test_07_queueSheetDismissal()` | Swipe-down or backpress smoothly dismisses Queue | **PASSED** |
| 8 | `test_08_playerControlsResponsive()` | Play/pause and playback controls remain responsive | **PASSED** |

### 8.2 Download Notification Test Suite
- **File:** [DownloadNotificationTest.kt](file:///c:/Users/Afsal/OneDrive/Desktop/LofiQ-Music/app/src/test/java/com/lofiq/data/download/DownloadNotificationTest.kt)

| # | Test Method Name | Specification Verified | Status |
|---|---|---|:---:|
| 1 | `test_01_startingDownloadCreatesProgressNotification()` | Download start initializes progress notification | **PASSED** |
| 2 | `test_02_progressPercentageReflectsActualProgress()` | Progress calculation accurately reflects bytes read | **PASSED** |
| 3 | `test_03_multipleDownloadsDistinctNotificationIds()` | Concurrent downloads have distinct notification IDs | **PASSED** |
| 4 | `test_04_notificationThrottling()` | Throttles rapid updates (< 2% or < 250ms) | **PASSED** |
| 5 | `test_05_successfulDownloadCancelsNotification()` | Successful download removes progress notification | **PASSED** |
| 6 | `test_06_failedDownloadCancelsNotification()` | Failed download removes progress notification | **PASSED** |
| 7 | `test_07_cancelledDownloadCancelsNotification()` | Cancelled download removes progress notification | **PASSED** |
| 8 | `test_08_deletedDownloadCancelsNotification()` | Deleted download removes progress notification | **PASSED** |
| 9 | `test_09_downloadedFilesRemainAccessibleInLibrary()` | File path and state preserved in database | **PASSED** |
| 10 | `test_10_musicPlaybackContinuesUninterrupted()` | LOW importance notification does not disrupt playback | **PASSED** |

### 8.3 Cumulative Test Execution Summary
```
> Task :app:testDebugUnitTest

com.lofiq.core.SmartQueueManagementTest (12 tests) .................... PASSED
com.lofiq.ui.navigation.HomeNavigationTest (10 tests) ................. PASSED
com.lofiq.feature.player.PlayerQueueGestureTest (8 tests) ............. PASSED
com.lofiq.data.download.DownloadNotificationTest (10 tests) ........... PASSED

BUILD SUCCESSFUL
40 tests completed, 0 failed, 0 skipped.
```

---

## 9. Verification Checklist

- [x] **Universal Context Menu:** Operational across all song-display surfaces.
- [x] **Deterministic Queue Logic:** Play Next (LIFO) and Add to Queue (FIFO) verified.
- [x] **Home Tab Reset:** Always clears backstack to root Home destination.
- [x] **Q Button Removal:** Visible Q button completely removed from player UI.
- [x] **Queue Swipe Gesture:** Smooth upward swipe gesture on player reveals QueueSheet.
- [x] **Native Download Notification:** Shows small icon, track title, and real-time progress bar.
- [x] **Notification Cleanup:** Automatically dismissed on completion, failure, or cancellation.
- [x] **Android TV Compatibility:** Universal package with Leanback banner and TV remote controls.
- [x] **Playback Continuity:** Ongoing music playback is never interrupted by gestures or notifications.

