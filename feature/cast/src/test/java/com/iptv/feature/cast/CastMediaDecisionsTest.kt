package com.iptv.feature.cast

import com.google.android.gms.cast.MediaStatus
import com.iptv.feature.cast.CastMediaDecisions
import com.iptv.feature.cast.CastMediaDecisions.DirectCastCompatibility
import com.iptv.core.network.StreamProber
import com.iptv.feature.cast.LiveTsHlsSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastMediaDecisionsTest {
    @Test
    fun `detects common direct receiver media types ignoring query and case`() {
        assertEquals("application/x-mpegURL", CastMediaDecisions.mimeTypeFor("https://tv.test/LIVE.M3U8?token=x"))
        assertEquals("application/dash+xml", CastMediaDecisions.mimeTypeFor("https://vod.test/movie.mpd"))
        assertEquals("video/mp4", CastMediaDecisions.mimeTypeFor("https://vod.test/movie.mp4#start"))
    }

    @Test
    fun `classifies direct URLs without rejecting extensionless http streams`() {
        assertEquals(DirectCastCompatibility.COMPATIBLE, CastMediaDecisions.directCastCompatibility("https://tv.test/live.m3u8"))
        assertEquals(DirectCastCompatibility.UNKNOWN, CastMediaDecisions.directCastCompatibility("http://tv.test/stream/42"))
        assertEquals(DirectCastCompatibility.INCOMPATIBLE, CastMediaDecisions.directCastCompatibility("file:///sdcard/video.mp4"))
    }

    @Test
    fun `xtream live ts urls prefer the hls variant when casting`() {
        assertEquals(
            "http://panel.test:8080/live/user/pass/42.m3u8",
            CastMediaDecisions.preferHlsForLive("http://panel.test:8080/live/user/pass/42.ts", isLive = true),
        )
        assertEquals(
            "http://panel.test:8080/live/user/pass/42.m3u8",
            CastMediaDecisions.preferHlsForLive("http://panel.test:8080/live/user/pass/42", isLive = true),
        )
        assertEquals(
            "http://panel.test:8080/live/user/pass/42.ts",
            CastMediaDecisions.preferHlsForLive("http://panel.test:8080/live/user/pass/42.ts", isLive = false),
        )
        // Non-Xtream and non-ts URLs are left untouched.
        assertEquals(
            "http://tv.test/channel.m3u8",
            CastMediaDecisions.preferHlsForLive("http://tv.test/channel.m3u8", isLive = true),
        )
    }

    @Test
    fun `payload sniffing detects real content regardless of labels`() {
        val playlist = "#EXTM3U\n#EXT-X-TARGETDURATION:10\nseg1.ts\n".toByteArray()
        assertEquals(
            StreamProber.Kind.HLS,
            StreamProber.sniffPayload(playlist, playlist.size),
        )
        // MPEG-TS packet: sync byte 0x47 then arbitrary payload.
        val ts = ByteArray(512).also { it[0] = 0x47 }
        assertEquals(
            StreamProber.Kind.TS,
            StreamProber.sniffPayload(ts, ts.size),
        )
        // MP4: ....ftyp
        val mp4 = "....ftypisom....".toByteArray()
        assertEquals(
            StreamProber.Kind.MP4,
            StreamProber.sniffPayload(mp4, mp4.size),
        )
        assertEquals(StreamProber.Kind.UNKNOWN, StreamProber.sniffPayload(ByteArray(0), 0))
    }

    @Test
    fun `synthetic live playlist lists window with sequence and durations`() {
        val segments = listOf(
            LiveTsHlsSession.Segment(3, ByteArray(10), 5.2),
            LiveTsHlsSession.Segment(4, ByteArray(10), 6.4),
        )
        val playlist = LiveTsHlsSession.buildPlaylist(segments, "http://192.168.1.10:8000/seg/abc")
        assertTrue(playlist.startsWith("#EXTM3U"))
        assertTrue(playlist.contains("#EXT-X-MEDIA-SEQUENCE:3"))
        assertTrue(playlist.contains("#EXTINF:5.200"))
        assertTrue(playlist.contains("http://192.168.1.10:8000/seg/abc/3.ts"))
        assertTrue(playlist.contains("http://192.168.1.10:8000/seg/abc/4.ts"))
        // TARGETDURATION must cover the longest segment.
        assertTrue(playlist.contains("#EXT-X-TARGETDURATION:7"))
        assertFalse(playlist.contains("#EXT-X-ENDLIST"))
    }

    @Test
    fun `running cast session is adopted only for the same stream`() {
        val playing = MediaStatus.PLAYER_STATE_PLAYING
        val paused = MediaStatus.PLAYER_STATE_PAUSED
        val idle = MediaStatus.PLAYER_STATE_IDLE

        // Re-entry on the same channel: keep the TV as-is.
        assertTrue(
            CastMediaDecisions.shouldAdoptRemotePlayback(playing, "http://tv/a", "http://tv/a"),
        )
        assertTrue(
            CastMediaDecisions.shouldAdoptRemotePlayback(paused, "http://tv/a", "http://tv/a"),
        )
        // Channel change: the new stream must be loaded on the receiver.
        assertFalse(
            CastMediaDecisions.shouldAdoptRemotePlayback(playing, "http://tv/a", "http://tv/b"),
        )
        // Idle receiver or unknown loaded item: never adopt.
        assertFalse(
            CastMediaDecisions.shouldAdoptRemotePlayback(idle, "http://tv/a", "http://tv/a"),
        )
        assertFalse(
            CastMediaDecisions.shouldAdoptRemotePlayback(playing, null, "http://tv/a"),
        )
    }

    @Test
    fun `declared container maps to probe kinds and ignores unknown values`() {
        assertEquals(StreamProber.Kind.TS, CastMediaDecisions.kindFromContainerExt("ts"))
        assertEquals(StreamProber.Kind.TS, CastMediaDecisions.kindFromContainerExt("TS"))
        assertEquals(StreamProber.Kind.HLS, CastMediaDecisions.kindFromContainerExt("m3u8"))
        assertEquals(StreamProber.Kind.MP4, CastMediaDecisions.kindFromContainerExt("mp4"))
        assertEquals(StreamProber.Kind.DASH, CastMediaDecisions.kindFromContainerExt("mpd"))
        // Containers the receiver can't use anyway still need a real probe.
        assertEquals(null, CastMediaDecisions.kindFromContainerExt("mkv"))
        assertEquals(null, CastMediaDecisions.kindFromContainerExt(null))
    }

    @Test
    fun `bandwidth estimate ranks variants by resolution`() {
        assertEquals(
            8_000_000,
            CastMediaDecisions.estimateBandwidth("#EXT-X-STREAM-INF:RESOLUTION=3840x2160"),
        )
        assertEquals(
            5_000_000,
            CastMediaDecisions.estimateBandwidth("#EXT-X-STREAM-INF:RESOLUTION=1920x1080,CODECS=\"a\""),
        )
        assertEquals(
            2_500_000,
            CastMediaDecisions.estimateBandwidth("#EXT-X-STREAM-INF:RESOLUTION=1280x720"),
        )
        assertEquals(
            1_200_000,
            CastMediaDecisions.estimateBandwidth("#EXT-X-STREAM-INF:RESOLUTION=720x576"),
        )
        // No resolution → safe low default, the receiver upshifts on its own.
        assertEquals(
            1_500_000,
            CastMediaDecisions.estimateBandwidth("#EXT-X-STREAM-INF:CODECS=\"avc1\""),
        )
    }

    @Test
    fun `fmp4 playlists are detected by init maps and m4s segments`() {
        val tsPlaylist = "#EXTM3U\n#EXT-X-TARGETDURATION:4\nseg1.ts\nseg2.ts\n"
        val fmp4Playlist =
            "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXT-X-TARGETDURATION:4\nseg1.m4s\n"
        val m4sPlaylist = "#EXTM3U\n#EXT-X-TARGETDURATION:4\nseg_1.m4s\nseg_2.m4s\n"
        assertFalse(StreamProber.looksLikeFmp4Hls(tsPlaylist))
        assertTrue(StreamProber.looksLikeFmp4Hls(fmp4Playlist))
        assertTrue(StreamProber.looksLikeFmp4Hls(m4sPlaylist))
    }

    @Test
    fun `mpd rewrite routes relative and absolute segment urls through proxy`() {
        val mpd = """<?xml version="1.0"?>
            |<MPD type="dynamic">
            |  <Period><AdaptationSet><Representation id="v">
            |    <SegmentTemplate media="seg_${'$'}Number${'$'}.m4s" initialization="init.mp4"/>
            |  </Representation></AdaptationSet></Period>
            |</MPD>""".trimMargin()
        val rewritten = rewriteMpdManifest(
            mpd,
            "http://panel.test:8080/dash/ch1/manifest.mpd",
            "http://192.168.1.10:8000/abcd1234",
        )
        // BaseURL injected pointing at the /pd/ route for the manifest dir.
        assertTrue(rewritten.contains("<BaseURL>http://192.168.1.10:8000/abcd1234/pd/"))
        // Templated relative attrs stay relative — resolved via the BaseURL.
        assertTrue(rewritten.contains("media=\"seg_${'$'}Number${'$'}.m4s\""))
        assertTrue(rewritten.contains("initialization=\"init.mp4\""))

        val absMpd = """<MPD><Period><AdaptationSet><Representation>
            |<SegmentTemplate media="/dash/ch1/seg_${'$'}Number${'$'}.m4s"
            | initialization="http://panel.test:8080/dash/ch1/init.mp4"
            | index="http://other.test/i.mp4"/>
            |</Representation></AdaptationSet></Period></MPD>""".trimMargin()
        val rewrittenAbs = rewriteMpdManifest(
            absMpd,
            "http://panel.test:8080/dash/ch1/manifest.mpd",
            "http://192.168.1.10:8000/abcd1234",
        )
        // Path-absolute and same-origin absolute refs go through /pa/ and /pd/.
        assertTrue(rewrittenAbs.contains("media=\"/abcd1234/pa/"))
        assertTrue(rewrittenAbs.contains("initialization=\"init.mp4\""))  // same-dir absolute → relative
        assertFalse(rewrittenAbs.contains("initialization=\"http://panel.test"))
        // Foreign origins pass through untouched.
        assertTrue(rewrittenAbs.contains("index=\"http://other.test/i.mp4\""))
    }

    @Test
    fun `cast implementation classes are packaged with player`() {
        val loader = javaClass.classLoader
        assertNotNull(Class.forName("androidx.media3.cast.CastPlayer", false, loader))
        assertNotNull(Class.forName("com.google.android.gms.cast.framework.CastContext", false, loader))
        assertNotNull(Class.forName("com.iptv.feature.cast.IptvCastOptionsProvider", false, loader))
    }
}
