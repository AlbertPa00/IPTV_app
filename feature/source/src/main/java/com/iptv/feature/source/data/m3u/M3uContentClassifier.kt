package com.iptv.feature.source.data.m3u

import com.iptv.core.common.text.TextNormalizer
import com.iptv.core.storage.entity.Kinds

object M3uContentClassifier {

    fun classify(channel: ParsedChannel): String {
        val explicitType = TextNormalizer.searchKey(channel.contentType.orEmpty())
        if (explicitType in seriesTypes) return Kinds.SERIES
        if (explicitType in onDemandTypes) return Kinds.VOD
        if (explicitType in liveTypes) return Kinds.LIVE

        val url = channel.url.lowercase()
        if ("/series/" in url) return Kinds.SERIES
        if ("/movie/" in url || "/vod/" in url) return Kinds.VOD

        // Listas prehechas etiquetan episodios en el nombre: "Show S01E02",
        // "Show 1x03", "Show T02E05". Es la señal de serie más fiable en M3U.
        if (episodePattern.containsMatchIn(channel.name)) return Kinds.SERIES

        val group = TextNormalizer.searchKey(channel.groupTitle.orEmpty())
        if (seriesMarkers.any { marker -> marker in group }) return Kinds.SERIES
        return if (onDemandMarkers.any { marker -> marker in group }) Kinds.VOD else Kinds.LIVE
    }

    private val episodePattern = Regex(
        """(?:[st]\d{1,2}\s*[ex]\d{1,3}|\b\d{1,2}x\d{1,3}\b)""",
        RegexOption.IGNORE_CASE,
    )

    private val liveTypes = setOf("live", "tv", "channel")
    private val onDemandTypes = setOf("movie", "vod", "film", "cine", "cinema")
    private val seriesTypes = setOf("series", "serie", "episode", "episodio", "show", "tvshow", "tv show")

    private val onDemandMarkers = setOf(
        "vod",
        "movie",
        "pelicula",
        "peliculas",
        "film",
        "filmes",
        "cine",
        "cinema",
        "estrenos",
        "video club",
    )

    // Ojo: "serie" a secas queda fuera porque "Serie A/B" es fútbol en
    // directo en muchas listas — un falso positivo muy común.
    private val seriesMarkers = setOf(
        "series",
        "temporada",
        "temporadas",
        "tv shows",
        "tvshows",
        "episodios",
        "miniseries",
        "serien",
    )
}
