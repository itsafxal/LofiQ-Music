package com.lofiq.widget

import com.lofiq.data.repository.LibraryRepository
import com.lofiq.playback.engine.PlaybackEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MusicWidgetEntryPoint {
    fun playbackEngine(): PlaybackEngine
    fun libraryRepository(): LibraryRepository
}

