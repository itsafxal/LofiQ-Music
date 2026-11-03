package com.lofiq.di

import com.lofiq.resolver.providers.DirectHttpProviderAdapter
import com.lofiq.resolver.providers.JioSaavnProviderAdapter
import com.lofiq.resolver.providers.ProviderAdapter
import com.lofiq.resolver.providers.youtube.YouTubeResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ResolverModule {

    @Provides
    @Singleton
    @ElementsIntoSet
    fun provideProviderAdapters(
        jioSaavnProvider: JioSaavnProviderAdapter,
        youTubeResolver: YouTubeResolver,
        directHttpProvider: DirectHttpProviderAdapter
    ): Set<ProviderAdapter> {
        return linkedSetOf(jioSaavnProvider, youTubeResolver, directHttpProvider)
    }
}
