package com.iptv.feature.player

import com.iptv.feature.player.ui.LocalPlaybackItemSpec
import com.iptv.feature.player.ui.PlaybackMediaItemFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMediaItemFactoryTest {
    // El MIME local queda siempre sin fijar (el payload decide); el fallback
    // por extensión para Cast vive en :feature:cast (CastMediaDecisionsTest).
    @Test
    fun `local live extensionless item leaves MIME unset`() {
        val item = localItem("https://tv.test/live/42", isLive = true)

        assertNull(item.mimeType)
        assertTrue(item.isLive)
    }

    @Test
    fun `local HLS item leaves MIME unset`() {
        val item = localItem("https://tv.test/channel.m3u8?token=abc", isLive = true)

        assertNull(item.mimeType)
    }

    @Test
    fun `local VOD item leaves MIME unset`() {
        val item = localItem("https://vod.test/movie.mp4", isLive = false)

        assertNull(item.mimeType)
        assertFalse(item.isLive)
    }

    private fun localItem(url: String, isLive: Boolean): LocalPlaybackItemSpec =
        PlaybackMediaItemFactory.spec(
            streamUrl = url,
            title = "Test",
            artworkUrl = null,
            isLive = isLive,
        )
}
