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

    private suspend fun seed(
        database: AppDatabase,
        channelName: (Int) -> String = { "Canal $it" },
    ): Long {
        val sourceId = database.sourceDao().insert(
            SourceEntity(type = SourceTypes.M3U_FILE, name = "test", url = "/x.m3u", isActive = true),
        )
        stageChannels(database, sourceId, channelName)
        return sourceId
    }

    private suspend fun stageChannels(
        database: AppDatabase,
        sourceId: Long,
        channelName: (Int) -> String,
    ) {
        database.channelDao().insertStaging(
            (1..303).map {
                val name = channelName(it)
                ChannelStagingEntity(
                    sourceId = sourceId,
                    externalId = "http://x/$it.m3u8",
                    name = name,
                    nameNorm = name.lowercase(),
                    streamUrl = "http://x/$it.m3u8",
                    groupTitle = "LIVE:TV | TDT",
                    kind = Kinds.LIVE,
                )
            },
        )
    }

    private suspend fun merge(
        database: AppDatabase,
        sourceId: Long,
        categoryName: String = "TV | TDT",
    ) {
        database.withTransaction {
            database.categoryDao().clearStaging(sourceId)
            database.categoryDao().insertStaging(
                listOf(
                    CategoryStagingEntity(
                        sourceId = sourceId,
                        externalId = "LIVE:TV | TDT",
                        kind = Kinds.LIVE,
                        name = categoryName,
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

    /**
     * Las columnas del usuario (renombre, ocultación, orden manual y los de
     * categorías) sobreviven a una resincronización que cambia los datos del
     * proveedor: el REPLACE del merge las arrastra como hace con isFavorite.
     */
    @Test
    fun mergePreservesUserCustomizations() = runBlocking {
        val database = db()
        try {
            val sourceId = seed(database)
            merge(database, sourceId)

            val dao = database.channelDao()
            val ch5 = dao.findByExternalId(sourceId, "http://x/5.m3u8")!!
            val ch7 = dao.findByExternalId(sourceId, "http://x/7.m3u8")!!
            val ch9 = dao.findByExternalId(sourceId, "http://x/9.m3u8")!!
            dao.rename(ch5.id, "Mi Canal", "mi canal")
            dao.setHidden(ch7.id, true)
            dao.updateAll(listOf(ch9.copy(customOrder = 7)))
            val cat = database.categoryDao().allBySource(sourceId).single()
            database.categoryDao().rename(cat.id, "Mi Grupo")

            // El proveedor renombra canales y la categoría en la siguiente
            // importación: dispara el REPLACE sobre esas filas.
            dao.clearStaging(sourceId)
            stageChannels(database, sourceId) { i ->
                when (i) {
                    5 -> "Canal Cinco"
                    9 -> "Canal Nueve"
                    10 -> "Canal Diez"
                    else -> "Canal $i"
                }
            }
            merge(database, sourceId, categoryName = "Nuevo TDT")

            val renamed = dao.findByExternalId(sourceId, "http://x/5.m3u8")!!
            assertEquals("Mi Canal", renamed.name)
            assertEquals("mi canal", renamed.nameNorm)
            assertEquals("Canal Cinco", renamed.providerName)
            assertEquals("canal cinco", renamed.providerNameNorm)

            // Sin renombre del usuario, el nombre sigue al proveedor.
            val tracked = dao.findByExternalId(sourceId, "http://x/10.m3u8")!!
            assertEquals("Canal Diez", tracked.name)

            // Orden manual y ocultación sobreviven al REPLACE.
            val reordered = dao.findByExternalId(sourceId, "http://x/9.m3u8")!!
            assertEquals(7, reordered.customOrder)
            assertEquals(true, dao.findByExternalId(sourceId, "http://x/7.m3u8")!!.hidden)

            val renamedCat = database.categoryDao().allBySource(sourceId).single()
            assertEquals("Mi Grupo", renamedCat.name)
            assertEquals("Nuevo TDT", renamedCat.providerName)
        } finally {
            database.close()
        }
    }
}
