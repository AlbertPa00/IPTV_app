package com.iptv.feature.player

import com.iptv.feature.player.cast.CastMediaDecisions
import com.iptv.feature.player.ui.LocalPlaybackItemSpec
import com.iptv.feature.player.ui.PlaybackMediaItemFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMediaItemFactoryTest {
    @Test
    fun `local live extensionless item leaves MIME unset while Cast gets TS fallback`() {
        val item = localItem("https://tv.test/live/42", isLive = true)

        assertNull(item.mimeType)
        assertTrue(item.isLive)
        // Extensionless live URLs are almost always raw MPEG-TS (Xtream
        // convention); the probe refines this guess at cast time.
        assertEquals("video/mp2t", CastMediaDecisions.mimeTypeForCast(item.streamUrl, item.isLive))
    }

    @Test
    fun `local HLS item leaves MIME unset while Cast infers HLS`() {
        val item = localItem("https://tv.test/channel.m3u8?token=abc", isLive = true)

        assertNull(item.mimeType)
        assertEquals("application/x-mpegURL", CastMediaDecisions.mimeTypeForCast(item.streamUrl, item.isLive))
    }

    @Test
    fun `local VOD item leaves MIME unset while Cast infers MP4`() {
        val item = localItem("https://vod.test/movie.mp4", isLive = false)

        assertNull(item.mimeType)
        assertFalse(item.isLive)
        assertEquals("video/mp4", CastMediaDecisions.mimeTypeForCast(item.streamUrl, item.isLive))
    }

    private fun localItem(url: String, isLive: Boolean): LocalPlaybackItemSpec =
        PlaybackMediaItemFactory.spec(
            streamUrl = url,
            title = "Test",
            artworkUrl = null,
            isLive = isLive,
        )
}
