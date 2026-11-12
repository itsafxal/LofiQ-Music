package com.lofiq.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lofiq.auth.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @javax.inject.Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class FadePlayer

    private fun createMediaSourceFactory(
        context: Context,
        httpClient: OkHttpClient,
        authRepository: AuthRepository
    ): DefaultMediaSourceFactory {
        val playbackHttpClient = httpClient.newBuilder()
            .addInterceptor { chain ->
                var request = chain.request()
                val host = request.url.host
                val urlStr = request.url.toString()
                if (host.contains("googlevideo.com")) {
                    val reqBuilder = request.newBuilder()
                    // Strip credentials & origin headers for CDN stream playback to prevent 403 Forbidden
                    reqBuilder.removeHeader("Cookie")
                    reqBuilder.removeHeader("Authorization")
                    reqBuilder.removeHeader("Referer")
                    reqBuilder.removeHeader("Origin")

                    val ua = when {
                        urlStr.contains("c=ANDROID_VR") -> "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                        urlStr.contains("c=ANDROID_MUSIC") -> "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
                        urlStr.contains("c=IOS") -> "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
                        urlStr.contains("c=WEB") || urlStr.contains("c=WEB_REMIX") -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                        else -> "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
                    }
                    reqBuilder.header("User-Agent", ua)
                    request = reqBuilder.build()
                } else if (host.contains("youtube.com") || host.contains("ytimg.com")) {
                    val reqBuilder = request.newBuilder()
                    val authHeaders = authRepository.getCachedAuthHeaders()
                    authHeaders["Cookie"]?.let { reqBuilder.header("Cookie", it) }
                    authHeaders["Authorization"]?.let { reqBuilder.header("Authorization", it) }
                    request = reqBuilder.build()
                }
                chain.proceed(request)
            }
            .build()

        val httpDataSourceFactory = OkHttpDataSource.Factory(playbackHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpDataSourceFactory)

        return DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
    }

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
        authRepository: AuthRepository
    ): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val mediaSourceFactory = createMediaSourceFactory(context, httpClient, authRepository)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs
                45_000, // maxBufferMs
                1_500,  // bufferForPlaybackMs
                3_000   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    @Provides
    @Singleton
    @FadePlayer
    fun provideFadePlayer(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
        authRepository: AuthRepository
    ): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val mediaSourceFactory = createMediaSourceFactory(context, httpClient, authRepository)

        // Symmetric full-track playback load control so Slot B performs identically after handover
        // while keeping memory footprint controlled during early background preloading.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                12_000, // minBufferMs
                35_000, // maxBufferMs
                1_500,  // bufferForPlaybackMs
                2_500   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, false) // Promoted to true atomically at handover
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }
}
