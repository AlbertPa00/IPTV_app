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
    fun migrateAll_1to5() {
        helper.createDatabase(DB_NAME, 1).close()
        helper.runMigrationsAndValidate(
            DB_NAME, 5, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        )
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
