package com.iptv.feature.player.ui

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

class LocalPlaybackItemSpec internal constructor(
    val streamUrl: String,
    val title: String,
    val artworkUrl: String?,
    val isLive: Boolean,
) {
    val mimeType: String? = null
}

object PlaybackMediaItemFactory {
    fun spec(
        streamUrl: String,
        title: String,
        artworkUrl: String?,
        isLive: Boolean,
    ): LocalPlaybackItemSpec = LocalPlaybackItemSpec(streamUrl, title, artworkUrl, isLive)

    fun create(spec: LocalPlaybackItemSpec): MediaItem = MediaItem.Builder()
        .setUri(spec.streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(spec.title)
                .setArtworkUri(spec.artworkUrl?.let(Uri::parse))
                .build(),
        )
        .apply {
            if (spec.isLive) {
                setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(3_000)
                        .setMaxPlaybackSpeed(1.02f)
                        .build(),
                )
            }
        }
        .build()
}
