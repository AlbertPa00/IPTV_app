package com.iptv.core.storage.di

import android.content.Context
import androidx.room.Room
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.PlaybackHistoryDao
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.db.AppDatabase
import com.iptv.core.storage.db.MIGRATION_1_2
import com.iptv.core.storage.db.MIGRATION_2_3
import com.iptv.core.storage.db.MIGRATION_3_4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "iptv.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides
    fun sourceDao(db: AppDatabase): SourceDao = db.sourceDao()

    @Provides
    fun categoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun channelDao(db: AppDatabase): ChannelDao = db.channelDao()

    @Provides
    fun programmeDao(db: AppDatabase): ProgrammeDao = db.programmeDao()

    @Provides
    fun playbackHistoryDao(db: AppDatabase): PlaybackHistoryDao = db.playbackHistoryDao()
}
