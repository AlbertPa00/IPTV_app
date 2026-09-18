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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `isLocked` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `language` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `language` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_kind_sortOrder_name` ON `channels` (`sourceId`, `kind`, `sortOrder`, `name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_kind_nameNorm` ON `channels` (`sourceId`, `kind`, `nameNorm`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId_kind_language` ON `channels` (`sourceId`, `kind`, `language`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_sourceId_kind` ON `categories` (`sourceId`, `kind`)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Cabeceras por canal (#EXTVLCOPT): muchos streams legales (RTVE, etc.)
        // exigen un User-Agent/Referer concreto o responden 403.
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `userAgent` TEXT")
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `referrer` TEXT")
    }
}

/**
 * `channels_fts` es FTS4 con contenido externo (`content=channels`): Room
 * crea y mantiene automáticamente los triggers `room_fts_content_sync_*`
 * tanto en `onCreate` como en `onOpen`. No crear triggers propios aquí:
 * duplicarían cada alta/baja en el índice.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Tablas de volcado para importaciones por lotes con merge por
        // (sourceId, externalId): conserva ids (historial/favoritos) y acota
        // la memoria del parseo a un lote.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `channels_staging` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sourceId` INTEGER NOT NULL,
                `externalId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `nameNorm` TEXT NOT NULL,
                `streamUrl` TEXT NOT NULL,
                `logoUrl` TEXT,
                `tvgId` TEXT,
                `groupTitle` TEXT,
                `kind` TEXT NOT NULL,
                `containerExt` TEXT,
                `sortOrder` INTEGER NOT NULL,
                `language` TEXT NOT NULL,
                `userAgent` TEXT,
                `referrer` TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_channels_staging_sourceId_externalId` ON `channels_staging` (`sourceId`, `externalId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `categories_staging` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sourceId` INTEGER NOT NULL,
                `externalId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `language` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_staging_sourceId_externalId` ON `categories_staging` (`sourceId`, `externalId`)")

        // Búsqueda FTS4 sobre nameNorm (la tabla ya viene poblada por
        // 'rebuild' desde el contenido externo; los triggers la mantienen).
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `channels_fts` " +
                "USING FTS4(`nameNorm` TEXT NOT NULL, content=`channels`)",
        )
        db.execSQL("INSERT INTO `channels_fts`(`channels_fts`) VALUES('rebuild')")
    }
}
