package com.iptv.core.storage.di

import android.content.Context
import androidx.room.Room
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.PlaybackHistoryDao
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.iptv.core.storage.db.AppDatabase
import com.iptv.core.storage.db.MIGRATION_1_2
import com.iptv.core.storage.db.MIGRATION_2_3
import com.iptv.core.storage.db.MIGRATION_3_4
import com.iptv.core.storage.db.MIGRATION_4_5
import com.iptv.core.storage.db.MIGRATION_5_6
import com.iptv.core.storage.db.MIGRATION_6_7
import com.iptv.core.storage.db.MIGRATION_7_8
import com.iptv.core.storage.db.MIGRATION_8_9
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
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            )
            // Los triggers de sincronización de channels_fts los crea Room
            // automáticamente (room_fts_content_sync_*). Esto solo retira los
            // channels_fts_ai/ad/au que instaló una beta intermedia: si alguno
            // existía, el índice pudo recibir altas duplicadas, así que se
            // reconstruye una única vez (el trigger ya no volverá a existir).
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    val hadLegacy = db.query(
                        "SELECT 1 FROM sqlite_master WHERE type='trigger' AND name='channels_fts_ai'",
                    ).use { it.moveToFirst() }
                    if (!hadLegacy) return
                    db.execSQL("DROP TRIGGER IF EXISTS `channels_fts_ai`")
                    db.execSQL("DROP TRIGGER IF EXISTS `channels_fts_ad`")
                    db.execSQL("DROP TRIGGER IF EXISTS `channels_fts_au`")
                    db.execSQL("INSERT INTO `channels_fts`(`channels_fts`) VALUES('rebuild')")
                }
            })
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
