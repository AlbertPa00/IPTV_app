package com.iptv.core.storage.db

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iptv.core.storage.entity.CategoryStagingEntity
import com.iptv.core.storage.entity.ChannelStagingEntity
import com.iptv.core.storage.entity.Kinds
import com.iptv.core.storage.entity.SourceEntity
import com.iptv.core.storage.entity.SourceTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Replica la secuencia de importación de SourceRepository.replaceCatalog:
 * volcado a staging + merge transaccional. Diagnóstico para el fallo de
 * importación en el dispositivo físico.
 */
@RunWith(AndroidJUnit4::class)
class MergeStagingTest {

    private fun db() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()

    private suspend fun seed(database: AppDatabase): Long {
        val sourceId = database.sourceDao().insert(
            SourceEntity(type = SourceTypes.M3U_FILE, name = "test", url = "/x.m3u", isActive = true),
        )
        database.channelDao().insertStaging(
            (1..303).map {
                ChannelStagingEntity(
                    sourceId = sourceId,
                    externalId = "http://x/$it.m3u8",
                    name = "Canal $it",
                    nameNorm = "canal $it",
                    streamUrl = "http://x/$it.m3u8",
                    groupTitle = "LIVE:TV | TDT",
                    kind = Kinds.LIVE,
                )
            },
        )
        return sourceId
    }

    private suspend fun merge(database: AppDatabase, sourceId: Long) {
        database.withTransaction {
            database.categoryDao().clearStaging(sourceId)
            database.categoryDao().insertStaging(
                listOf(
                    CategoryStagingEntity(
                        sourceId = sourceId,
                        externalId = "LIVE:TV | TDT",
                        kind = Kinds.LIVE,
                        name = "TV | TDT",
                    ),
                ),
            )
            database.categoryDao().mergeStagedUpdates(sourceId)
            database.categoryDao().insertStagedNew(sourceId)
            database.categoryDao().deleteAbsent(sourceId)
            database.channelDao().mergeStagedUpdates(sourceId)
            database.channelDao().insertStagedNew(sourceId)
            database.channelDao().deleteAbsent(sourceId)
            database.channelDao().clearStaging(sourceId)
            database.categoryDao().clearStaging(sourceId)
            database.sourceDao().markSynced(sourceId, System.currentTimeMillis())
        }
    }

    @Test
    fun mergeInTransaction() = runBlocking {
        val database = db()
        try {
            val sourceId = seed(database)
            merge(database, sourceId)
            assertEquals(303, database.channelDao().countBySource(sourceId))
        } finally {
            database.close()
        }
    }

    @Test
    fun mergeInsideFlowOnIo() = runBlocking {
        val database = db()
        try {
            val sourceId = seed(database)
            flow {
                merge(database, sourceId)
                emit(Unit)
            }.flowOn(Dispatchers.IO).collect { }
            assertEquals(303, database.channelDao().countBySource(sourceId))
        } finally {
            database.close()
        }
    }
}
