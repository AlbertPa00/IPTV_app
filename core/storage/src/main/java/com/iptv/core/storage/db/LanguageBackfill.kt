package com.iptv.core.storage.db

import androidx.room.withTransaction
import com.iptv.core.common.dispatchers.AppDispatchers
import com.iptv.core.common.text.LanguageTag
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rellena la columna `language` de catálogos importados antes de la migración
 * a v6. Es idempotente: solo toca filas con `language = ''`, así que llamarla
 * al arrancar es barato cuando no hay nada pendiente.
 */
@Singleton
class LanguageBackfill @Inject constructor(
    private val database: AppDatabase,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val dispatchers: AppDispatchers,
) {
    suspend fun run() = withContext(dispatchers.io) {
        if (categoryDao.withoutLanguage().isEmpty() && !channelDao.hasWithoutLanguage()) {
            return@withContext
        }
        database.withTransaction {
            categoryDao.withoutLanguage().forEach {
                categoryDao.setLanguage(it.id, LanguageTag.detect(it.name))
            }
            // Canales con categoría: heredan su idioma en una sola consulta.
            channelDao.propagateLanguageFromCategories()
            // Resto (sin categoría o categoría sin señal): detección por nombre.
            channelDao.withoutLanguage()
                .mapNotNull { row ->
                    LanguageTag.detect(row.name)
                        .takeIf { it.isNotBlank() }
                        ?.let { row.id to it }
                }
                .groupBy({ it.second }, { it.first })
                .forEach { (language, ids) ->
                    ids.chunked(500).forEach { channelDao.setLanguageForIds(it, language) }
                }
        }
    }
}
