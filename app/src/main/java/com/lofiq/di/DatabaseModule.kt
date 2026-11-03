package com.lofiq.di

import android.content.Context
import androidx.room.Room
import com.lofiq.core.database.LofiQDatabase
import com.lofiq.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLofiQDatabase(
        @ApplicationContext context: Context
    ): LofiQDatabase {
        return Room.databaseBuilder(
            context,
            LofiQDatabase::class.java,
            LofiQDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideSongDao(database: LofiQDatabase): SongDao = database.songDao()

    @Provides
    fun provideFavoriteDao(database: LofiQDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providePlaylistDao(database: LofiQDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideHistoryDao(database: LofiQDatabase): PlaybackHistoryDao = database.historyDao()

    @Provides
    fun provideQueueSnapshotDao(database: LofiQDatabase): QueueSnapshotDao = database.queueSnapshotDao()

    @Provides
    fun provideMetadataCacheDao(database: LofiQDatabase): MetadataCacheDao = database.metadataCacheDao()
}
