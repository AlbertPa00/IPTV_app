package com.iptv.feature.player.cast

import androidx.media3.common.MimeTypes
import com.google.android.gms.cast.MediaStatus

/** Pure decisions shared by local and Cast playback. */
object CastMediaDecisions {
    enum class DirectCastCompatibility { COMPATIBLE, UNKNOWN, INCOMPATIBLE }

    fun mimeTypeFor(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            path.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
            path.endsWith(".mp3") -> MimeTypes.AUDIO_MPEG
            path.endsWith(".aac") -> MimeTypes.AUDIO_AAC
            else -> null
        }
    }

    fun mimeTypeForCast(url: String, isLive: Boolean): String =
        // Extensionless live URLs are almost always raw MPEG-TS (Xtream and
        // most panels default to TS); HLS virtually always keeps .m3u8.
        mimeTypeFor(url) ?: if (isLive) MimeTypes.VIDEO_MP2T else MimeTypes.VIDEO_MP4

    fun directCastCompatibility(url: String): DirectCastCompatibility {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (scheme != "http" && scheme != "https") return DirectCastCompatibility.INCOMPATIBLE
        return if (mimeTypeFor(url) == null) {
            DirectCastCompatibility.UNKNOWN
        } else {
            DirectCastCompatibility.COMPATIBLE
        }
    }

    /**
     * Xtream Codes panels expose the same live stream as raw MPEG-TS
     * (`/live/user/pass/123.ts` or extensionless `.../123`) and as real HLS
     * (`.../123.m3u8`). The HLS variant is far more reliable on a Chromecast
     * (native retries, playlist refresh, ABR), so for casting we prefer it
     * when the URL matches the Xtream live pattern.
     */
    fun preferHlsForLive(url: String, isLive: Boolean): String {
        if (!isLive) return url
        return XTREAM_LIVE.replace(url) { "${it.groupValues[1]}.m3u8" }
    }

    private val XTREAM_LIVE = Regex("""(/live/[^/]+/[^/]+/\d+)(?:\.[a-zA-Z0-9]+)?$""")

    /**
     * Whether an in-flight Cast session can be adopted without reloading:
     * only when the receiver is actively on a stream AND that stream is
     * the one this screen wants ([loadedStreamUri] == [itemStreamUri]).
     * Any other case — idle receiver, or a different channel — must load
     * fresh so changing channels actually changes what's on the TV.
     */
    fun shouldAdoptRemotePlayback(
        remotePlayerState: Int?,
        loadedStreamUri: String?,
        itemStreamUri: String?,
    ): Boolean =
        (remotePlayerState == MediaStatus.PLAYER_STATE_PLAYING ||
            remotePlayerState == MediaStatus.PLAYER_STATE_BUFFERING ||
            remotePlayerState == MediaStatus.PLAYER_STATE_PAUSED) &&
            loadedStreamUri != null && loadedStreamUri == itemStreamUri
}
