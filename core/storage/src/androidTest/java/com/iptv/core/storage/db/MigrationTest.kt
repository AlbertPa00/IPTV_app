package com.iptv.core.storage.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate4To5_addsHiddenColumnKeepingData() {
        helper.createDatabase(DB_NAME, 4).apply {
            execSQL(
                "INSERT INTO sources (id, type, name, isActive) VALUES (1, 'M3U_URL', 'S1', 1)",
            )
            execSQL(
                "INSERT INTO categories (sourceId, externalId, kind, name, sortOrder, isLocked) " +
                    "VALUES (1, 'g1', 'LIVE', 'Grupo 1', 2, 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5)

        db.query("SELECT sortOrder, isLocked, hidden FROM categories").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
            assertEquals(1, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }
    }

    @Test
    fun migrate5To6_addsLanguageColumnsKeepingData() {
        helper.createDatabase(DB_NAME, 5).apply {
            execSQL(
                "INSERT INTO sources (id, type, name, isActive) VALUES (1, 'M3U_URL', 'S1', 1)",
            )
            execSQL(
                "INSERT INTO categories (sourceId, externalId, kind, name, sortOrder, isLocked, hidden) " +
                    "VALUES (1, 'g1', 'LIVE', 'ES - TDT', 0, 0, 0)",
            )
            execSQL(
                "INSERT INTO channels (sourceId, externalId, name, nameNorm, streamUrl, kind, sortOrder, isFavorite) " +
                    "VALUES (1, 'u1', 'La 1', 'la 1', 'http://x', 'LIVE', 0, 0)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 6, true, MIGRATION_5_6)

        db.query("SELECT name, language FROM categories").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("ES - TDT", c.getString(0))
            assertEquals("", c.getString(1))
        }
        db.query("SELECT name, language FROM channels").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("La 1", c.getString(0))
            assertEquals("", c.getString(1))
        }
    }

    @Test
    fun migrate6To7_addsCatalogIndexes() {
        helper.createDatabase(DB_NAME, 6).close()

        val db = helper.runMigrationsAndValidate(DB_NAME, 7, true, MIGRATION_6_7)

        db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='channels'").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertTrue(names.contains("index_channels_sourceId_kind_sortOrder_name"))
            assertTrue(names.contains("index_channels_sourceId_kind_nameNorm"))
            assertTrue(names.contains("index_channels_sourceId_kind_language"))
        }
    }

    @Test
    fun migrate7To8_addsChannelHeaderColumns() {
        helper.createDatabase(DB_NAME, 7).apply {
            execSQL(
                "INSERT INTO sources (id, type, name, isActive) VALUES (1, 'M3U_URL', 'S1', 1)",
            )
            execSQL(
                "INSERT INTO channels (sourceId, externalId, name, nameNorm, streamUrl, kind, sortOrder, isFavorite, language) " +
                    "VALUES (1, 'u1', 'La 1', 'la 1', 'http://x', 'LIVE', 0, 0, '')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 8, true, MIGRATION_7_8)

        db.query("SELECT name, userAgent, referrer FROM channels").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("La 1", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
    }

    @Test
    fun migrate8To9_createsStagingAndFts() {
        helper.createDatabase(DB_NAME, 8).apply {
            execSQL(
                "INSERT INTO sources (id, type, name, isActive) VALUES (1, 'M3U_URL', 'S1', 1)",
            )
            execSQL(
                "INSERT INTO channels (sourceId, externalId, name, nameNorm, streamUrl, kind, sortOrder, isFavorite, language) " +
                    "VALUES (1, 'u1', 'La 1', 'la 1', 'http://x', 'LIVE', 0, 1, '')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 9, true, MIGRATION_8_9)

        // La FTS se rellena desde el contenido existente y conserva las filas.
        db.query("SELECT nameNorm FROM channels_fts WHERE channels_fts MATCH 'la*'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("la 1", c.getString(0))
        }
        db.query("SELECT isFavorite FROM channels").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun migrateAll_1to9() {
        helper.createDatabase(DB_NAME, 1).close()
        helper.runMigrationsAndValidate(
            DB_NAME, 9, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        )
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
