package com.iptv.feature.source.data.xtream

import okhttp3.HttpUrl.Companion.toHttpUrl

object XtreamStreamUrlBuilder {

    fun live(baseUrl: String, username: String, password: String, streamId: Long, extension: String?): String =
        build(baseUrl, "live", username, password, streamId, extension ?: "ts")

    fun movie(baseUrl: String, username: String, password: String, streamId: Long, extension: String?): String =
        build(baseUrl, "movie", username, password, streamId, extension ?: "mp4")

    private fun build(
        baseUrl: String,
        type: String,
        username: String,
        password: String,
        streamId: Long,
        extension: String,
    ): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(type)
        .addPathSegment(username)
        .addPathSegment(password)
        .addPathSegment("$streamId.$extension")
        .build()
        .toString()
}
