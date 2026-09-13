package com.iptv.core.storage.db

import androidx.room.withTransaction
import com.iptv.core.common.dispatchers.AppDispatchers
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.common.text.LanguageTag
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Etiqueta `language` (familia: "EN", "ES", "AR"…) en categorías y canales.
 *
 * Va por versión de detector (`LanguageTag.DETECTOR_VERSION`): cuando sube,
 * re-etiqueta TODO el catálogo porque el significado del código cambia
 * (de país suelto a familia agrupada). Canal sin señal propia hereda el
 * idioma de su categoría. Es una sola transacción en segundo plano.
 */
@Singleton
class LanguageBackfill @Inject constructor(
    private val database: AppDatabase,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val prefs: AppPreferences,
    private val dispatchers: AppDispatchers,
) {
    suspend fun run() = withContext(dispatchers.io) {
        if (prefs.languageTagVersion() >= LanguageTag.DETECTOR_VERSION) return@withContext
        database.withTransaction {
            // 1. Categorías: etiqueta por nombre de grupo.
            val categoryLanguage = categoryDao.allIdName()
                .associate { it.id to LanguageTag.detect(it.name) }
            categoryLanguage.forEach { (id, language) ->
                categoryDao.setLanguage(id, language)
            }
            // 2. Canales: señal propia del nombre o herencia de la categoría.
            channelDao.allIdNameCategory()
                .map { row ->
                    row.id to LanguageTag.detect(row.name)
                        .ifBlank { categoryLanguage[row.categoryId].orEmpty() }
                }
                .groupBy({ it.second }, { it.first })
                .forEach { (language, ids) ->
                    ids.chunked(500).forEach { channelDao.setLanguageForIds(it, language) }
                }
        }
        prefs.setLanguageTagVersion(LanguageTag.DETECTOR_VERSION)
    }
}
