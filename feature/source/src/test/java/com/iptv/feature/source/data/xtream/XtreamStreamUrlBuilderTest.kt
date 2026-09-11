package com.iptv.feature.source.data.xtream

import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamStreamUrlBuilderTest {

    @Test
    fun `builds live url with provider extension`() {
        val url = XtreamStreamUrlBuilder.live(
            baseUrl = "https://provider.test:8443/",
            username = "user",
            password = "pass",
            streamId = 42,
            extension = "m3u8",
        )

        assertEquals("https://provider.test:8443/live/user/pass/42.m3u8", url)
    }

    @Test
    fun `uses live ts fallback`() {
        val url = XtreamStreamUrlBuilder.live("http://provider.test:8080", "u", "p", 7, null)

        assertEquals("http://provider.test:8080/live/u/p/7.ts", url)
    }

    @Test
    fun `builds movie url with mp4 fallback`() {
        val url = XtreamStreamUrlBuilder.movie("http://provider.test", "u", "p", 8, null)

        assertEquals("http://provider.test/movie/u/p/8.mp4", url)
    }

    @Test
    fun `encodes credentials used as path segments`() {
        val url = XtreamStreamUrlBuilder.live("http://provider.test", "user name", "p/ss", 9, "ts")

        assertEquals("http://provider.test/live/user%20name/p%2Fss/9.ts", url)
    }
}
