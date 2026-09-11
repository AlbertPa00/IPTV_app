package com.iptv.core.storage.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `programmes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sourceId` INTEGER NOT NULL,
                `channelKey` TEXT NOT NULL,
                `channelNameNorm` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `startUtc` INTEGER NOT NULL,
                `endUtc` INTEGER NOT NULL,
                `iconUrl` TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_sourceId` ON `programmes` (`sourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_sourceId_channelKey` ON `programmes` (`sourceId`, `channelKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_sourceId_channelNameNorm` ON `programmes` (`sourceId`, `channelNameNorm`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_sourceId_startUtc_endUtc` ON `programmes` (`sourceId`, `startUtc`, `endUtc`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `playback_history` (
                `channelId` INTEGER PRIMARY KEY NOT NULL,
                `sourceId` INTEGER NOT NULL,
                `positionMs` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_sourceId` ON `playback_history` (`sourceId`)")
    }
}
