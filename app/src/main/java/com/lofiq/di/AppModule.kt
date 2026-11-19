package com.lofiq.di

import com.lofiq.core.common.DefaultDispatcherProvider
import com.lofiq.core.common.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DispatcherModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(provider: DefaultDispatcherProvider): DispatcherProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        diagnosticsTracker: com.lofiq.data.sync.SyncDiagnosticsTracker
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val diagnosticsInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            val requestBytes = request.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
            val response = chain.proceed(request)
            val responseBytes = response.body?.contentLength()?.coerceAtLeast(0L) ?: 0L
            diagnosticsTracker.recordHttpRequest(requestBytes + responseBytes)
            response
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(diagnosticsInterceptor)
            .addInterceptor(logging)
            .build()
    }
}
