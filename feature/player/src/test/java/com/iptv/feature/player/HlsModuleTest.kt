package com.iptv.feature.player

import org.junit.Assert.assertNotNull
import org.junit.Test

class HlsModuleTest {

    @Test
    fun `hls media source is packaged with player`() {
        assertNotNull(Class.forName("androidx.media3.exoplayer.hls.HlsMediaSource\$Factory"))
    }
}
