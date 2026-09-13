package com.iptv.core.common.text

import java.util.Locale

/**
 * Detecta el idioma/país de un canal o categoría de IPTV.
 *
 * Los proveedores Xtream/M3U suelen prefijar los nombres de categoría (y a
 * veces los canales) con un código de país: "AR - ...", "US | ...",
 * "UK: ...". También usan códigos de panel que no son ISO ("EXYU", "ASIA",
 * "NF" = Netflix…). Como segunda señal se acepta un código ISO al FINAL del
 * nombre ("Series ES") y, sin más, el alfabeto (árabe, cirílico, CJK…).
 * Devuelve el código en mayúsculas o "" si no hay señal fiable.
 */
object LanguageTag {

    /** Token inicial de 2-6 letras seguido de separador típico de panel. */
    private val PREFIX = Regex("""^([A-Za-z]{2,6})\s*(?:[-|•·:/\\]|\s{2,})""")

    /** Código de panel multi-letra seguido de espacio simple ("ASIA MOVIES"). */
    private val PANEL_PREFIX = Regex("""^([A-Za-z]{3,6})\s""")

    /** Código ISO al final del nombre: "Series ES", "… (UK)". */
    private val SUFFIX = Regex("""[\s\-|•·:/\\(]\s*([A-Za-z]{2,3})\s*\)?\s*$""")

    /** Etiquetas de calidad/marketing que no son idiomas. */
    private val NON_LANGUAGE = setOf(
        "4K", "8K", "UHD", "FHD", "HD", "SD", "HEVC", "VIP", "XXX",
        "RAW", "VOD", "TV", "PPV", "NBA", "NFL", "NHL", "MLB", "UFC",
        "DARK", "CINEMA", "SPORTS", "TOP", "WWE",
    )

    /**
     * Códigos de panel que no son países ISO pero agrupan contenido:
     * regiones (EXYU, SCANDI…), idiomas usados como prefijo (EN) y marcas
     * que el panel usa como prefijo (NF=Netflix, MV/MC=música, RX=relax…).
     */
    private val PANEL = setOf(
        "LATAM", "LATINO", "CARIBE", "EXYU", "SCANDI", "NORDIC", "ASIA",
        "AFR", "KURD", "INT", "WORLD", "EU", "EN",
        // UK no es ISO (ISO usa GB) pero es el código estándar en paneles.
        "UK",
        // Códigos de panel que agrupan por marca/contenido, no por país.
        "NF", "MV", "MC", "RX", "TS", "WT", "SU", "AS",
    )

    /** Países ISO-3166 reales (cubre AF, GE, AZ, UZ, KZ, SR… que faltaban). */
    private val ISO: Set<String> = Locale.getISOCountries().toSet()

    private val KNOWN: Set<String> = ISO + PANEL

    /** Alias frecuentes de paneles. */
    private val ALIAS = mapOf(
        "USA" to "US", "LATINO" to "LA", "LATAM" to "LA", "CARIBE" to "LA",
        "NORDIC" to "SCANDI", "KURD" to "KU", "GB" to "UK",
    )

    fun detect(name: String): String {
        val trimmed = name.trimStart()
        PREFIX.find(trimmed)?.groupValues?.get(1)?.uppercase()?.let { raw ->
            val code = ALIAS[raw] ?: raw
            if (raw !in NON_LANGUAGE && code in KNOWN) return code
        }
        // Prefijos de panel sin separador duro: "ASIA MOVIES", "LATINO 24/7".
        PANEL_PREFIX.find(trimmed)?.groupValues?.get(1)?.uppercase()?.let { raw ->
            if (raw in PANEL) return ALIAS[raw] ?: raw
        }
        // Sufijo: "Series ES", "Peliculas ES", "Documental (UK)".
        SUFFIX.find(name.trimEnd())?.groupValues?.get(1)?.uppercase()?.let { raw ->
            if (raw !in NON_LANGUAGE && raw in KNOWN) return raw
        }
        return scriptOf(trimmed)
    }

    /** Alfabeto dominante como respaldo cuando no hay prefijo de país. */
    private fun scriptOf(text: String): String {
        for (ch in text) {
            when (ch.code) {
                in 0x0600..0x06FF, in 0x0750..0x077F, in 0xFB50..0xFDFF -> return "AR"
                in 0x0400..0x04FF -> return "RU"
                in 0x0590..0x05FF -> return "HE"
                in 0x0370..0x03FF -> return "GR"
                in 0x4E00..0x9FFF -> return "CN"
                in 0x3040..0x30FF -> return "JP"
                in 0xAC00..0xD7AF -> return "KR"
                in 0x0900..0x097F -> return "IN"
                in 0x0E00..0x0E7F -> return "TH"
            }
        }
        return ""
    }
}
