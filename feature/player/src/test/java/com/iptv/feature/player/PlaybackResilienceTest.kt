package com.iptv.feature.player

import androidx.media3.common.PlaybackException
import com.iptv.feature.player.ui.PlaybackResilience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackResilienceTest {

    // -- isRetriableError ----------------------------------------------------

    @Test
    fun `network and timeout errors are retriable`() {
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null))
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null))
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_TIMEOUT, null))
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, null))
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null))
    }

    @Test
    fun `server http errors are retriable`() {
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 500))
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 503))
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 429))
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 408))
        // Sin código de respuesta no se puede descartar que sea un 5xx.
        assertTrue(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, null))
    }

    @Test
    fun `client http and format errors are fatal`() {
        assertFalse(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404))
        assertFalse(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403))
        assertFalse(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, null))
        assertFalse(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, null))
        assertFalse(PlaybackResilience.isRetriableError(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, null))
    }

    // -- reconnectDelayMs -----------------------------------------------------

    @Test
    fun `reconnect backoff doubles until the cap`() {
        assertEquals(1_000L, PlaybackResilience.reconnectDelayMs(1))
        assertEquals(2_000L, PlaybackResilience.reconnectDelayMs(2))
        assertEquals(4_000L, PlaybackResilience.reconnectDelayMs(3))
        assertEquals(8_000L, PlaybackResilience.reconnectDelayMs(4))
        assertEquals(10_000L, PlaybackResilience.reconnectDelayMs(5))
        // Sin overflow ni retroceso en intentos altos.
        assertEquals(10_000L, PlaybackResilience.reconnectDelayMs(50))
    }

    // -- bufferParams ----------------------------------------------------------

    @Test
    fun `buffer profiles are consistent and ordered`() {
        val low = PlaybackResilience.bufferParams(PlaybackResilience.PROFILE_LOW)
        val auto = PlaybackResilience.bufferParams(PlaybackResilience.PROFILE_AUTO)
        val high = PlaybackResilience.bufferParams(PlaybackResilience.PROFILE_HIGH)

        listOf(low, auto, high).forEach {
            assertTrue(it.minBufferMs <= it.maxBufferMs)
            assertTrue(it.bufferForPlaybackMs <= it.bufferForPlaybackAfterRebufferMs)
        }
        // La señal inestable pide más colchón tras rebuffer y más offset en directo.
        assertTrue(high.bufferForPlaybackAfterRebufferMs > auto.bufferForPlaybackAfterRebufferMs)
        assertTrue(high.liveTargetOffsetMs > auto.liveTargetOffsetMs)
        // El perfil bajo arranca antes que el auto.
        assertTrue(low.bufferForPlaybackMs < auto.bufferForPlaybackMs)

        // Perfil desconocido → auto.
        assertEquals(auto, PlaybackResilience.bufferParams("anything"))
    }
}
