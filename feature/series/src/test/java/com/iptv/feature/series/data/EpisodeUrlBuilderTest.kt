package com.iptv.feature.series.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeUrlBuilderTest {
    @Test
    fun `builds encoded xtream series url`() {
        val url = EpisodeUrlBuilder.build(
            baseUrl = "https://provider.example:8443",
            username = "user name",
            password = "p@ss/word",
            episodeId = "12345",
            extension = ".mkv",
        )

        assertEquals(
            "https://provider.example:8443/series/user%20name/p@ss%2Fword/12345.mkv",
            url,
        )
    }

    @Test
    fun `uses safe default for invalid extension`() {
        assertEquals(
            "http://provider/series/u/p/9.mp4",
            EpisodeUrlBuilder.build("http://provider", "u", "p", "9", "../ts"),
        )
    }
}
