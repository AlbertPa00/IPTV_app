package com.iptv.feature.player.cast

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Turns a raw live MPEG-TS stream into a sliding-window HLS playlist.
 *
 * The Chromecast Default Media Receiver cannot play progressive
 * `video/mp2t` (it returns LOAD_FAILED almost immediately) — MPEG-TS is
 * only supported *inside* HLS, where the receiver's own demuxer handles
 * the segments. This session fetches the upstream TS once, cuts it into
 * 188-byte-packet-aligned segments that start on a PAT, and serves a live
 * media playlist the receiver polls like any normal HLS endpoint.
 */
class LiveTsHlsSession(
    private val targetUrl: String,
    private val userAgent: String,
    private val referrer: String?,
) : LiveHlsSource {

    data class Segment(val seq: Long, val bytes: ByteArray, val durationSec: Double)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // A live stream stalls == dead stream; 30 s without a single byte
        // is treated as failure instead of hanging forever.
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val lock = Object()
    private val segments = LinkedHashMap<Long, Segment>()

    @Volatile var firstSeq = 0L; private set
    @Volatile var lastCompleteSeq = -1L; private set
    @Volatile override var finished = false; private set
    @Volatile override var failure: String? = null; private set
    @Volatile override var lastAccessMs = System.currentTimeMillis()

    private var upstreamCall: okhttp3.Call? = null
    private var started = false

    @Synchronized
    override fun start() {
        if (started) return
        started = true
        thread(name = "hls-segmenter", isDaemon = true) { run() }
    }

    override fun close() {
        runCatching { upstreamCall?.cancel() }
        synchronized(lock) { segments.clear() }
        finished = true
    }

    override fun openSegment(name: String): Pair<InputStream, Long>? {
        val seq = name.removeSuffix(".ts").toLongOrNull() ?: return null
        val segment = segment(seq) ?: return null
        return ByteArrayInputStream(segment.bytes) to segment.bytes.size.toLong()
    }

    // -- playlist / segment API used by the HTTP server -------------------

    /** Completed segments in the live window, oldest first. */
    fun windowSegments(): List<Segment> = synchronized(lock) { segments.values.toList() }

    fun segment(seq: Long): Segment? = synchronized(lock) { segments[seq] }

    /** Blocks briefly until the first segment exists (startup fast path). */
    override fun awaitFirstSegment(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (lastCompleteSeq >= 0 || failure != null) return lastCompleteSeq >= 0
            Thread.sleep(150)
        }
        return lastCompleteSeq >= 0
    }

    override fun playlist(segmentBaseUrl: String): String =
        buildPlaylist(windowSegments(), segmentBaseUrl)

    // -- segmenter ---------------------------------------------------------

    private fun run() {
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", userAgent)
            .apply { referrer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .build()
        try {
            val call = client.newCall(request)
            upstreamCall = call
            call.execute().use { response ->
                if (!response.isSuccessful || response.body == null) {
                    failure = "HTTP ${response.code}"
                    return
                }
                Log.d(TAG, "Upstream connected: $targetUrl (${response.header("Content-Type")})")
                segmentLoop(response.body!!.byteStream())
            }
        } catch (e: Exception) {
            if (!finished) failure = e.message ?: e.javaClass.simpleName
        } finally {
            finished = true
        }
    }

    private fun segmentLoop(stream: InputStream) {
        val packet = ByteArray(TS_PACKET_BYTES)
        var current = ByteArrayOutputStream(INITIAL_TARGET_BYTES)
        var segmentStartMs = System.currentTimeMillis()
        var seq = 0L
        var targetBytes = INITIAL_TARGET_BYTES
        var firstPacket = true

        while (true) {
            if (!fillPacket(stream, packet)) break
            if (packet[0] != TS_SYNC_BYTE) {
                // Misaligned data — try to resync on the first packet only.
                // A text body here means the panel sent an error page
                // (e.g. "max connections"), which must fail loudly.
                if (firstPacket && resync(stream, packet)) {
                    Log.w(TAG, "Resynced to TS alignment after leading garbage")
                } else {
                    failure = "upstream is not aligned MPEG-TS"
                    break
                }
            }
            firstPacket = false

            val isPat = isPatPacket(packet)
            val pending = current.size()
            if (pending > 0 &&
                ((pending >= targetBytes && isPat) || pending >= targetBytes * FORCE_CUT_FACTOR)
            ) {
                val now = System.currentTimeMillis()
                val durationSec = (now - segmentStartMs) / 1000.0
                pushSegment(seq, current.toByteArray(), durationSec)
                seq++
                current = ByteArrayOutputStream(targetBytes * 2)
                segmentStartMs = now
                // Adapt the byte target so segments land near TARGET_DURATION_SEC.
                if (durationSec > 0.5) {
                    targetBytes = (pending / durationSec * TARGET_DURATION_SEC)
                        .toInt()
                        .coerceIn(MIN_TARGET_BYTES, MAX_TARGET_BYTES)
                }
            }
            current.write(packet)
        }
        // Flush whatever remains so late joiners still get a last segment.
        if (current.size() >= TS_PACKET_BYTES * 100) {
            pushSegment(seq, current.toByteArray(), TARGET_DURATION_SEC.toDouble())
        }
        if (lastCompleteSeq < 0 && failure == null) {
            failure = "upstream ended before any segment was produced"
        }
    }

    private fun fillPacket(stream: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = stream.read(buf, off, buf.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    /** Shift the buffer left until byte 0 is the TS sync byte (bounded). */
    private fun resync(stream: InputStream, buf: ByteArray): Boolean {
        repeat(buf.size * 4) {
            System.arraycopy(buf, 1, buf, 0, buf.size - 1)
            if (stream.read(buf, buf.size - 1, 1) < 0) return false
            if (buf[0] == TS_SYNC_BYTE) return true
        }
        return false
    }

    /** PAT = PID 0 with payload-unit-start set — a clean demuxer resync point. */
    private fun isPatPacket(packet: ByteArray): Boolean {
        val payloadStart = packet[1].toInt() and 0x40 != 0
        val pid = (packet[1].toInt() and 0x1F shl 8) or (packet[2].toInt() and 0xFF)
        return payloadStart && pid == 0
    }

    private fun pushSegment(seq: Long, bytes: ByteArray, durationSec: Double) {
        synchronized(lock) {
            segments[seq] = Segment(seq, bytes, durationSec)
            lastCompleteSeq = seq
            while (segments.size > WINDOW_SEGMENTS) {
                segments.remove(segments.keys.first())
            }
            firstSeq = segments.keys.first()
        }
        Log.d(TAG, "Segment $seq ready: ${bytes.size} bytes, ${durationSec}s")
    }

    companion object {
        /** Pure playlist builder — unit-testable without network. */
        fun buildPlaylist(window: List<Segment>, segmentBaseUrl: String): String {
            val targetDuration = maxOf(
                TARGET_DURATION_SEC,
                (window.maxOfOrNull { it.durationSec } ?: 0.0).toInt() + 1,
            )
            return buildString {
                appendLine("#EXTM3U")
                appendLine("#EXT-X-VERSION:3")
                appendLine("#EXT-X-TARGETDURATION:$targetDuration")
                appendLine("#EXT-X-MEDIA-SEQUENCE:${window.firstOrNull()?.seq ?: 0L}")
                for (seg in window) {
                    appendLine("#EXTINF:${String.format(Locale.US, "%.3f", seg.durationSec)},")
                    appendLine("$segmentBaseUrl/${seg.seq}.ts")
                }
            }
        }

        private const val TAG = "LiveTsHls"
        private const val TS_PACKET_BYTES = 188
        private const val TS_SYNC_BYTE: Byte = 0x47
        private const val TARGET_DURATION_SEC = 6
        private const val WINDOW_SEGMENTS = 5
        // ~750 KB gets the first segment out fast; the target then adapts to
        // the measured bitrate so segments land near TARGET_DURATION_SEC.
        private const val INITIAL_TARGET_BYTES = 768 * 1024
        private const val MIN_TARGET_BYTES = 512 * 1024
        private const val MAX_TARGET_BYTES = 8 * 1024 * 1024
        private const val FORCE_CUT_FACTOR = 3
    }
}
