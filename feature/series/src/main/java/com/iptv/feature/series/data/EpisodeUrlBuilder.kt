package com.iptv.feature.series.data

import okhttp3.HttpUrl.Companion.toHttpUrl

object EpisodeUrlBuilder {
    fun build(
        baseUrl: String,
        username: String,
        password: String,
        episodeId: String,
        extension: String,
    ): String {
        val safeExtension = extension.trim().removePrefix(".")
            .takeIf { it.matches(Regex("[A-Za-z0-9]+")) } ?: "mp4"
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(username)
            .addPathSegment(password)
            .addPathSegment("$episodeId.$safeExtension")
            .build()
            .toString()
    }
}
