package com.iptv.feature.player.cast

import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import android.net.Uri

/**
 * Converts Media3 MediaItems to Cast MediaQueueItems with correct stream type
 * and metadata for the Default Media Receiver.
 *
 * Handles missing fields gracefully — older Cast devices and some converter
 * versions may leave contentUrl unset.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LiveAwareMediaItemConverter : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem =
        delegate.toMediaItem(mediaQueueItem)

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val isLive = mediaItem.liveConfiguration != MediaItem.LiveConfiguration.UNSET
        // The sender sets an explicit mimeType computed from the original
        // stream URL; guessing from the (rewritten) URI misdetects proxied
        // or signed URLs whose extension isn't the last path segment.
        val mimeType = mediaItem.localConfiguration?.mimeType
            ?: CastMediaDecisions.mimeTypeForCast(
                url = mediaItem.localConfiguration?.uri.toString(),
                isLive = isLive,
            )

        val castItem = mediaItem.buildUpon()
            .setMimeType(mimeType)
            .build()
        val original = delegate.toMediaQueueItem(castItem)
        val info = requireNotNull(original.media)

        val builder = MediaInfo.Builder(info.contentId)
            .setContentType(info.contentType ?: mimeType)
            .setStreamType(
                if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED,
            )

        // The Default Media Receiver needs to know the HLS segment format to
        // demux correctly. IPTV live HLS almost always uses MPEG-TS segments.
        // setHlsSegmentFormat takes a String per HlsSegmentFormat annotation.
        if (mimeType == MimeTypes.APPLICATION_M3U8) {
            builder.setHlsSegmentFormat("TS")
            builder.setHlsVideoSegmentFormat("MPEG2_TS")
        }

        info.contentUrl?.let { builder.setContentUrl(it) }
        info.customData?.let { builder.setCustomData(it) }

        val title = mediaItem.mediaMetadata.title?.toString()
        if (title != null) {
            val meta = info.metadata ?: MediaMetadata(
                if (isLive) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE,
            )
            meta.putString(MediaMetadata.KEY_TITLE, title)
            mediaItem.mediaMetadata.artworkUri?.toString()?.let { artworkUrl ->
                runCatching { meta.addImage(WebImage(Uri.parse(artworkUrl))) }
            }
            builder.setMetadata(meta)
        } else {
            info.metadata?.let { builder.setMetadata(it) }
        }

        return MediaQueueItem.Builder(builder.build()).build()
    }
}
