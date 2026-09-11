package com.iptv.feature.player

import com.iptv.feature.player.cast.CastMediaDecisions
import com.iptv.feature.player.cast.CastMediaDecisions.DirectCastCompatibility
import com.iptv.feature.player.cast.CastStreamProber
import com.iptv.feature.player.cast.LiveTsHlsSession
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
            CastStreamProber.Kind.HLS,
            CastStreamProber.sniffPayload(playlist, playlist.size),
        )
        // MPEG-TS packet: sync byte 0x47 then arbitrary payload.
        val ts = ByteArray(512).also { it[0] = 0x47 }
        assertEquals(
            CastStreamProber.Kind.TS,
            CastStreamProber.sniffPayload(ts, ts.size),
        )
        // MP4: ....ftyp
        val mp4 = "....ftypisom....".toByteArray()
        assertEquals(
            CastStreamProber.Kind.MP4,
            CastStreamProber.sniffPayload(mp4, mp4.size),
        )
        assertEquals(CastStreamProber.Kind.UNKNOWN, CastStreamProber.sniffPayload(ByteArray(0), 0))
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
    fun `cast implementation classes are packaged with player`() {
        val loader = javaClass.classLoader
        assertNotNull(Class.forName("androidx.media3.cast.CastPlayer", false, loader))
        assertNotNull(Class.forName("com.google.android.gms.cast.framework.CastContext", false, loader))
        assertNotNull(Class.forName("com.iptv.feature.player.cast.IptvCastOptionsProvider", false, loader))
    }
}
