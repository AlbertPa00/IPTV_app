package com.iptv.feature.player.cast

import java.io.InputStream

/**
 * A live source that exposes its content as HLS: a media playlist plus
 * MPEG-TS segments. Implemented by [LiveTsHlsSession] (in-memory packet
 * cutting) and [FfmpegHlsSession] (ffmpeg remux/transcode to disk).
 */
interface LiveHlsSource {

    var lastAccessMs: Long
    val failure: String?
    val finished: Boolean

    fun start()
    fun close()

    /** Blocks briefly until the first segment exists (startup fast path). */
    fun awaitFirstSegment(timeoutMs: Long): Boolean

    /** Live playlist text; segment lines must point under [segmentBaseUrl]. */
    fun playlist(segmentBaseUrl: String): String

    /**
     * Opens a segment referenced by the playlist. [name] is the segment
     * file name (e.g. `3.ts` or `s00007.ts`); the implementation maps it to
     * its own storage. Returns stream + content length, or null if gone.
     */
    fun openSegment(name: String): Pair<InputStream, Long>?
}
