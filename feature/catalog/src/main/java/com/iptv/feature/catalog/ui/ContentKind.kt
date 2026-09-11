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
