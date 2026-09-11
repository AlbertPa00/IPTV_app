package com.iptv.feature.player.cast

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live HLS source backed by FFmpeg: fetches the upstream stream and writes
 * a spec-compliant sliding HLS playlist + segments to [workDir].
 *
 * Two modes:
 *  - remux (`-c copy`): packets pass through untouched — fixes container
 *    issues, zero CPU cost. Default for raw TS live streams.
 *  - transcodeAudio (`-c:v copy -c:a aac`): re-encodes audio to AAC — fixes
 *    MP2/AC3 audio that the Chromecast can't decode. Video stays untouched.
 *
 * (Video transcode for MPEG-2 sources would need a GPL FFmpegKit variant
 * with libx264; not bundled.)
 */
class FfmpegHlsSession(
    private val targetUrl: String,
    private val userAgent: String,
    private val referrer: String?,
    private val workDir: File,
    private val transcodeAudio: Boolean,
) : LiveHlsSource {

    @Volatile override var lastAccessMs = System.currentTimeMillis()
    @Volatile override var failure: String? = null; private set
    @Volatile override var finished = false; private set
    @Volatile private var lastGoodPlaylist: String? = null

    private val started = AtomicBoolean(false)
    private var session: FFmpegSession? = null

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        try {
            workDir.mkdirs()
            workDir.listFiles()?.forEach { it.delete() }
            val playlistPath = File(workDir, PLAYLIST_NAME).absolutePath
            val segmentPattern = File(workDir, "s%05d.ts").absolutePath

            val cmd = buildString {
                append("-hide_banner -loglevel warning ")
                append("-user_agent ").appendArg(userAgent).append(' ')
                referrer?.takeIf { it.isNotBlank() }?.let {
                    append("-headers ").appendArg("Referer: $it\r\n").append(' ')
                }
                append("-reconnect 1 -reconnect_streamed 1 -reconnect_at_eof 1 ")
                append("-reconnect_delay_max 5 ")
                append("-i ").appendArg(targetUrl).append(' ')
                if (transcodeAudio) {
                    append("-c:v copy -c:a aac -b:a 128k ")
                } else {
                    append("-c copy ")
                }
                append("-f hls -hls_time 4 -hls_list_size 5 ")
                append("-hls_flags delete_segments+omit_endlist+independent_segments+temp_file ")
                append("-hls_segment_filename ").appendArg(segmentPattern).append(' ')
                appendArg(playlistPath)
            }
            Log.d(TAG, "ffmpeg start: $cmd")
            session = FFmpegKit.executeAsync(
                cmd,
                { completed ->
                    finished = true
                    if (!ReturnCode.isSuccess(completed.returnCode)) {
                        failure = "ffmpeg exited rc=${completed.returnCode}"
                        Log.e(TAG, "ffmpeg failed: $failure")
                        completed.allLogsAsString.takeLast(600).let { Log.e(TAG, it) }
                    }
                },
                { log -> Log.d(TAG, log.message ?: "") },
                { },
            )
        } catch (t: Throwable) {
            finished = true
            failure = "ffmpeg unavailable: ${t.message}"
            Log.e(TAG, "ffmpeg start failed", t)
        }
    }

    override fun close() {
        runCatching { session?.cancel() }
        session = null
        workDir.listFiles()?.forEach { it.delete() }
        runCatching { workDir.delete() }
        finished = true
    }

    override fun awaitFirstSegment(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (hasSegmentInPlaylist() || failure != null) return hasSegmentInPlaylist()
            Thread.sleep(200)
        }
        return hasSegmentInPlaylist()
    }

    private fun hasSegmentInPlaylist(): Boolean {
        val file = File(workDir, PLAYLIST_NAME)
        if (!file.isFile) return false
        val text = runCatching { file.readText() }.getOrNull() ?: return false
        return text.lines().any { it.isNotBlank() && !it.startsWith("#") }
    }

    override fun playlist(segmentBaseUrl: String): String {
        val file = File(workDir, PLAYLIST_NAME)
        val text = runCatching { file.readText() }.getOrNull()
        // ffmpeg rewrites index.m3u8 in place; a torn read can yield a file
        // without the header — serve the last complete copy in that case.
        if (text == null || !text.startsWith("#EXTM3U")) {
            return lastGoodPlaylist ?: "#EXTM3U\n#EXT-X-TARGETDURATION:6\n"
        }
        val rewritten = text.lines().joinToString("\n") { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) line else "$segmentBaseUrl/${t.substringAfterLast('/')}"
        }
        lastGoodPlaylist = rewritten
        return rewritten
    }

    override fun openSegment(name: String): Pair<InputStream, Long>? {
        // Segments live flat in workDir; strip any path to avoid traversal.
        val file = File(workDir, File(name).name)
        if (!file.isFile) return null
        return FileInputStream(file) to file.length()
    }

    private fun StringBuilder.appendArg(arg: String): StringBuilder =
        append('"').append(arg.replace("\"", "\\\"")).append('"')

    private companion object {
        const val TAG = "FfmpegHls"
        const val PLAYLIST_NAME = "index.m3u8"
    }
}
