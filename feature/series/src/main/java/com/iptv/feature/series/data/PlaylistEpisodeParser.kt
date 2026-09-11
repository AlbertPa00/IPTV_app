package com.iptv.feature.series.data

import com.iptv.core.common.text.TextNormalizer

/**
 * Extrae temporada/episodio del nombre de una entrada M3U de series
 * ("Show S01E02", "Show T02E05", "Show 1x03") y devuelve el título base
 * para agrupar episodios hermanos de la misma serie.
 *
 * Las listas M3U no ofrecen un API de episodios como Xtream: cada entrada
 * es un stream suelto, así que la agrupación se deduce del nombre.
 */
object PlaylistEpisodeParser {

    data class Parsed(val baseTitle: String, val season: Int, val episode: Int)

    private val patterns = listOf(
        Regex("""[st](\d{1,2})\s*[ex](\d{1,3})""", RegexOption.IGNORE_CASE),
        Regex("""\b(\d{1,2})x(\d{1,3})\b""", RegexOption.IGNORE_CASE),
    )

    fun parse(name: String): Parsed? {
        for (pattern in patterns) {
            val match = pattern.find(name) ?: continue
            val season = match.groupValues[1].toIntOrNull() ?: continue
            val episode = match.groupValues[2].toIntOrNull() ?: continue
            val base = name.removeRange(match.range)
                .replace(Regex("\\s+"), " ")
                .trimEnd(' ', '-', '_', '.', '·')
                .ifBlank { name }
            return Parsed(base, season, episode)
        }
        return null
    }

    private val tagPattern = Regex("""\([^)]*\)|\[[^]]*]""")
    private val qualityTokens = Regex(
        """\b(4k|2160p|1080p|720p|480p|uhd|fhd|hdtv|web[- ]?dl|webrip|bluray|bdrip|hdrip|dvdrip|x264|x265|hevc|hdr10|hdr)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Clave normalizada del título base: dos entradas son episodios de la
     *  misma serie si sus claves coinciden. Las etiquetas de año/país/
     *  calidad solo se ignoran cuando el nombre lleva patrón de episodio;
     *  sin él ("Película (2026) (GB)") el nombre completo es la clave, para
     *  no fusionar títulos distintos. */
    fun baseKey(name: String): String {
        val parsed = parse(name) ?: return TextNormalizer.searchKey(name)
        val cleaned = qualityTokens.replace(tagPattern.replace(parsed.baseTitle, " "), " ")
        return TextNormalizer.searchKey(cleaned)
    }
}
