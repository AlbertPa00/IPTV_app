package com.iptv.core.common.text

import java.text.Normalizer

/**
 * Normaliza texto para búsquedas y claves: minúsculas y sin acentos/diacríticos.
 * Ejemplo: "Canal+ Acción" -> "canal+ accion"
 */
object TextNormalizer {

    private val combiningMarks = Regex("\\p{Mn}+")

    fun searchKey(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase()
            .trim()
}
