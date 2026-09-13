package com.iptv.feature.player.cast

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Probes the first bytes of a stream to learn what the server actually
 * serves. IPTV endpoints routinely lie: `.m3u8` URLs that return raw
 * MPEG-TS when the panel has HLS output disabled, extensionless URLs that
 * serve TS, `application/octet-stream` Content-Types, etc. Declaring the
 * wrong type to the Cast receiver is fatal — the URL's extension and the
 * Content-Type header are only hints; the payload prefix is the truth.
 */
object CastStreamProber {

    enum class Kind { HLS, TS, MP4, DASH, UNKNOWN }

    private const val SNIFF_BYTES = 512
    private const val TS_SYNC: Byte = 0x47

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Fetches the first bytes of [url] and sniffs the payload.
     * Never throws; returns [Kind.UNKNOWN] on any failure.
     */
    fun probe(url: String, userAgent: String, referrer: String?): Kind = runCatching {
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-4095")
            .apply { referrer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Probe HTTP ${response.code} for ${redactUrl(url)}")
                return@use Kind.UNKNOWN
            }
            val head = ByteArray(SNIFF_BYTES)
            val n = readUpTo(response.body!!.byteStream(), head)
            val sniffed = sniffPayload(head, n)
            if (sniffed != Kind.UNKNOWN) {
                sniffed
            } else {
                kindFromContentType(response.header("Content-Type"))
            }
        }
    }.onFailure { Log.w(TAG, "Probe failed for ${redactUrl(url)}", it) }
        .getOrDefault(Kind.UNKNOWN)

    /**
     * Sniffs an already-read payload prefix — same logic the proxy uses to
     * decide whether a response is really a playlist.
     */
    fun sniffPayload(head: ByteArray, n: Int): Kind = when {
        n >= 1 && head[0] == TS_SYNC -> Kind.TS
        n >= 8 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte() -> Kind.MP4
        else -> {
            val text = String(head, 0, n, Charsets.UTF_8)
                .trimStart { it == '﻿' || it.isWhitespace() }
            when {
                text.startsWith("#EXTM3U") -> Kind.HLS
                text.startsWith("<MPD") || text.startsWith("<?xml") && text.contains("<MPD") -> Kind.DASH
                else -> Kind.UNKNOWN
            }
        }
    }

    private fun kindFromContentType(contentType: String?): Kind = when {
        contentType == null -> Kind.UNKNOWN
        contentType.contains("mpegurl") -> Kind.HLS
        contentType.contains("mp2t") || contentType.contains("mpeg") -> Kind.TS
        contentType.contains("dash") -> Kind.DASH
        contentType.contains("mp4") -> Kind.MP4
        else -> Kind.UNKNOWN
    }

    private fun readUpTo(stream: InputStream, buf: ByteArray): Int {
        var total = 0
        return try {
            while (total < buf.size) {
                val n = stream.read(buf, total, buf.size - total)
                if (n < 0) break
                total += n
                // 188 bytes = one full TS packet; enough for every signature.
                if (total >= 188) break
            }
            total
        } catch (_: SocketTimeoutException) {
            total
        } catch (_: IOException) {
            total
        }
    }

    private const val TAG = "CastStreamProber"
}
