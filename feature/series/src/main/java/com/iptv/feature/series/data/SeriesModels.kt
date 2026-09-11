package com.iptv.feature.series.data

data class SeriesDetails(
    val title: String,
    val coverUrl: String?,
    val plot: String?,
    val rating: String?,
    val seasons: List<SeriesSeason>,
)

data class SeriesSeason(
    val number: Int,
    val title: String,
    val episodes: List<SeriesEpisode>,
)

data class SeriesEpisode(
    val id: String,
    val number: Int,
    val title: String,
    val plot: String?,
    val imageUrl: String?,
    val rating: String?,
    val duration: String?,
    val extension: String,
)
