package com.iptv.feature.catalog.data

/** Metadatos de una película obtenidos de `get_vod_info` (Xtream Codes). */
data class VodInfo(
    val title: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val rating: String?,
    val releaseDate: String?,
    val duration: String?,
    val backdropUrl: String?,
    val country: String?,
    val ageRating: String?,
) {
    val year: String?
        get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)
}
