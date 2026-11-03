package com.lofiq.resolver.prefetch

import com.lofiq.core.common.DispatcherProvider
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.model.AudioQuality
import com.lofiq.core.model.Song
import com.lofiq.resolver.core.ResolveRequest
import com.lofiq.resolver.manager.ResolverManager
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefetchManager @Inject constructor(
    private val resolverManager: ResolverManager,
    private val dispatchers: DispatcherProvider
) {
    private val prefetchScope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private var activePrefetchJob: Job? = null

    fun prefetchNextTracks(nextSongs: List<Song>, quality: AudioQuality = AudioQuality.HIGH) {
        activePrefetchJob?.cancel()
        if (nextSongs.isEmpty()) return

        activePrefetchJob = prefetchScope.launch {
            // Prefetch up to 2 next songs in order
            for (song in nextSongs.take(2)) {
                if (!isActive) break
                try {
                    val cleanArtist = song.artist
                        .replace(Regex("""\d{1,2}:\d{2}(:\d{2})?$"""), "")
                        .replace(Regex("""(?i)\(Original Motion Picture Soundtrack\)|\[Original Motion Picture Soundtrack\]"""), "")
                        .replace(Regex("""(?i)\(From ".*?"\)|\(From '.*?'\)"""), "")
                        .trim()
                        .ifBlank { song.artist }

                    val request = ResolveRequest(
                        songId = song.id,
                        songTitle = song.title,
                        artistName = cleanArtist,
                        albumName = song.album,
                        durationSeconds = song.durationSeconds,
                        preferredQuality = quality
                    )
                    val result = resolverManager.resolve(request)
                    LofiQLogger.i(LofiQLogger.Category.PREFETCH, "Prefetched stream successfully for: '${song.title}' -> ${result.streamUrl.take(40)}...")
                } catch (e: CancellationException) {
                    LofiQLogger.d(LofiQLogger.Category.PREFETCH, "Prefetch cancelled for: '${song.title}'")
                    break
                } catch (e: Exception) {
                    LofiQLogger.w(LofiQLogger.Category.PREFETCH, "Prefetch failed for '${song.title}': ${e.message}")
                }
            }
        }
    }

    fun cancelAll() {
        activePrefetchJob?.cancel()
    }
}
