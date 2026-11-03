package com.iptv.feature.tvlink

import com.iptv.feature.tvlink.wire.AuthFailReason
import com.iptv.feature.tvlink.wire.LinkAction
import com.iptv.feature.tvlink.wire.LinkMsg
import com.iptv.feature.tvlink.wire.NowPlayingDto
import com.iptv.feature.tvlink.wire.RemoteChannelDto
import com.iptv.feature.tvlink.wire.RemoteSourceDto
import com.iptv.feature.tvlink.wire.linkJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round-trip del codec NDJSON + tolerancia a versiones distintas. */
class LinkProtocolTest {

    private fun roundTrip(msg: LinkMsg): LinkMsg =
        linkJson.decodeFromString(LinkMsg.serializer(), linkJson.encodeToString(LinkMsg.serializer(), msg))

    @Test
    fun `hello con pin hace round-trip`() {
        val msg = LinkMsg.Hello(v = 1, deviceId = "abc", deviceName = "Pixel", pin = "123456")
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `state con nowPlaying hace round-trip`() {
        val msg = LinkMsg.State(
            nowPlaying = NowPlayingDto(
                channelId = 7, sourceId = 2, name = "La 1",
                logoUrl = "http://x/logo.png", isLive = true,
            ),
            isPlaying = true,
            positionMs = 42_000,
        )
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `cmd hace round-trip`() {
        val msg = LinkMsg.Cmd(
            action = LinkAction.PLAY, channelId = 9, positionMs = 5_000,
        )
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `cmd seek to hace round-trip`() {
        val msg = LinkMsg.Cmd(action = LinkAction.SEEK_TO, positionMs = 95_000)
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `tick con duración hace round-trip`() {
        val msg = LinkMsg.Tick(positionMs = 12_000, playing = true, durationMs = 7_200_000)
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `tick sin duración decodifica desde versiones antiguas`() {
        val old = """{"type":"tick","positionMs":100,"playing":true}"""
        assertEquals(LinkMsg.Tick(100, true, 0), linkJson.decodeFromString<LinkMsg>(old))
    }

    @Test
    fun `campos desconocidos se ignoran (host más nuevo que el cliente)`() {
        val future = """{"type":"tick","positionMs":100,"playing":true,"nuevoCampo":5}"""
        val msg = linkJson.decodeFromString<LinkMsg>(future)
        assertEquals(LinkMsg.Tick(100, true), msg)
    }

    @Test
    fun `mensaje desconocido lanza y el read loop lo descarta`() {
        val unknown = """{"type":"mensaje_futuro","x":1}"""
        val decoded = runCatching { linkJson.decodeFromString<LinkMsg>(unknown) }
        assertTrue(decoded.isFailure)
    }

    @Test
    fun `auth fail con motivo`() {
        val msg = LinkMsg.AuthFail(AuthFailReason.PIN_WRONG)
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `campos por defecto no se serializan`() {
        val raw = linkJson.encodeToString(
            LinkMsg.serializer(),
            LinkMsg.Cmd(LinkAction.PAUSE),
        )
        assertNull(Regex("channelId").find(raw))
    }

    @Test
    fun `cmd catalog page hace round-trip`() {
        val msg = LinkMsg.Cmd(
            action = LinkAction.CATALOG_PAGE, reqId = 3, kind = "VOD",
            query = "dragon", group = "g42", favoritesOnly = true,
            offset = 300, limit = 150,
        )
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `cmd favorite set hace round-trip`() {
        val msg = LinkMsg.Cmd(
            action = LinkAction.FAVORITE_SET,
            channelKey = "xt:live:42", channelName = "La 2", favorite = true,
        )
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `cmd source add xtream hace round-trip`() {
        val msg = LinkMsg.Cmd(
            action = LinkAction.SOURCE_ADD_XTREAM, opId = "xc-9",
            sourceName = "Mi server", server = "host:8080",
            username = "u", password = "p",
        )
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `catalog page respuesta hace round-trip`() {
        val msg = LinkMsg.CatalogPage(
            reqId = 7, kind = "LIVE",
            items = listOf(
                RemoteChannelDto(key = "k1", name = "Canal", kind = "LIVE", fav = true),
            ),
            total = 500, offset = 0, done = false,
        )
        assertEquals(msg, roundTrip(msg))
    }

    @Test
    fun `sources y source op hacen round-trip`() {
        val sources = LinkMsg.Sources(
            listOf(
                RemoteSourceDto(
                    key = "XTREAM|h:80|u", type = "XTREAM", name = "S1",
                    active = true, channels = 1234, lastSyncAt = 99L,
                ),
            ),
        )
        assertEquals(sources, roundTrip(sources))
        val op = LinkMsg.SourceOp(opId = "m3u-1", phase = "progress", step = "IMPORTING", count = 50)
        assertEquals(op, roundTrip(op))
    }
}
