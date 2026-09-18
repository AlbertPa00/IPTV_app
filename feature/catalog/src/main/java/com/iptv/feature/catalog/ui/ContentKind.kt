package com.iptv.feature.catalog.ui

import com.iptv.core.common.text.TextNormalizer
import com.iptv.core.storage.entity.Kinds

/** Secciones visuales del catálogo y el `kind` persistido en Room. */
enum class ContentKind(val storageValue: String) {
    TV(Kinds.LIVE),
    MOVIES(Kinds.VOD),
    SERIES(Kinds.SERIES),
}

/** Normaliza el texto del usuario al patrón LIKE usado por las consultas. */
internal fun String.toLikePattern(): String = TextNormalizer.searchKey(this)
    .replace("\\", "\\\\")
    .replace("%", "\\%")
    .replace("_", "\\_")

/**
 * Convierte la búsqueda del usuario a una consulta FTS MATCH: cada palabra
 * normalizada se reduce a caracteres alfanuméricos y lleva prefijo `*`
 * (`hbo* max*`), que es AND implícito con coincidencia de prefijo.
 * Vacío si no quedan tokens útiles (la consulta recae al LIKE).
 */
internal fun String.toFtsMatch(): String = TextNormalizer.searchKey(this)
    .split(Regex("[^\\p{L}\\p{N}]+"))
    .filter { it.isNotBlank() }
    .joinToString(" ") { "$it*" }
