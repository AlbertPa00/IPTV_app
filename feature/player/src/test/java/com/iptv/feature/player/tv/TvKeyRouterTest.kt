package com.iptv.feature.player.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class TvKeyRouterTest {

    private fun ctx(
        overlay: TvOverlay = TvOverlay.NONE,
        isLive: Boolean = true,
        hasChannelList: Boolean = true,
    ) = TvInputContext(overlay = overlay, isLive = isLive, hasChannelList = hasChannelList)

    private fun down(code: Int) = TvKey(code, TvKeyAction.DOWN)
    private fun up(code: Int) = TvKey(code, TvKeyAction.UP)

    private fun perform(result: TvKeyResult): TvAction =
        (result as TvKeyResult.Perform).action

    // -- Sin overlay: flechas ejecutan acciones -----------------------------

    @Test
    fun `center on bare video opens controls`() {
        assertEquals(
            TvAction.OpenControls,
            perform(routeTvKey(down(TvKeys.DPAD_CENTER), ctx())),
        )
    }

    @Test
    fun `up and down zap when channel list exists`() {
        assertEquals(TvAction.ZapBy(+1), perform(routeTvKey(down(TvKeys.DPAD_UP), ctx())))
        assertEquals(TvAction.ZapBy(-1), perform(routeTvKey(down(TvKeys.DPAD_DOWN), ctx())))
    }

    @Test
    fun `up and down are ignored without channel list`() {
        assertEquals(
            TvKeyResult.Ignore,
            routeTvKey(down(TvKeys.DPAD_UP), ctx(hasChannelList = false)),
        )
    }

    @Test
    fun `channel rocker keys zap`() {
        assertEquals(TvAction.ZapBy(+1), perform(routeTvKey(down(TvKeys.CHANNEL_UP), ctx())))
        assertEquals(TvAction.ZapBy(-1), perform(routeTvKey(down(TvKeys.CHANNEL_DOWN), ctx())))
    }

    @Test
    fun `left opens rail and right opens info`() {
        assertEquals(TvAction.OpenRail, perform(routeTvKey(down(TvKeys.DPAD_LEFT), ctx())))
        assertEquals(TvAction.OpenInfo, perform(routeTvKey(down(TvKeys.DPAD_RIGHT), ctx())))
    }

    @Test
    fun `back on bare video exits`() {
        assertEquals(TvAction.Back, perform(routeTvKey(down(TvKeys.BACK), ctx())))
    }

    // -- Teclas de función ---------------------------------------------------

    @Test
    fun `media play pause toggles playback in any overlay`() {
        TvOverlay.values().forEach { overlay ->
            assertEquals(
                "overlay=$overlay",
                TvAction.TogglePlayPause,
                perform(routeTvKey(down(TvKeys.MEDIA_PLAY_PAUSE), ctx(overlay = overlay))),
            )
        }
    }

    @Test
    fun `digits produce digit action in any overlay`() {
        TvOverlay.values().forEach { overlay ->
            assertEquals(
                "overlay=$overlay",
                TvAction.Digit(7),
                perform(routeTvKey(down(TvKeys.DIGIT_0 + 7), ctx(overlay = overlay))),
            )
        }
    }

    @Test
    fun `rewind seeks only when not live`() {
        assertEquals(
            TvKeyResult.Ignore,
            routeTvKey(down(TvKeys.MEDIA_REWIND), ctx(isLive = true)),
        )
        assertEquals(
            TvAction.SeekBy(-30_000L),
            perform(routeTvKey(down(TvKeys.MEDIA_REWIND), ctx(isLive = false))),
        )
    }

    // -- Overlays enfocables: las flechas pertenecen al foco ------------------

    @Test
    fun `center is ignored inside focusable overlays so focused button clicks`() {
        assertEquals(
            TvKeyResult.Ignore,
            routeTvKey(down(TvKeys.DPAD_CENTER), ctx(overlay = TvOverlay.CONTROLS)),
        )
        assertEquals(
            TvKeyResult.Ignore,
            routeTvKey(down(TvKeys.DPAD_CENTER), ctx(overlay = TvOverlay.RAIL)),
        )
    }

    @Test
    fun `arrows are ignored inside controls so focus navigates buttons`() {
        listOf(TvKeys.DPAD_UP, TvKeys.DPAD_DOWN, TvKeys.DPAD_LEFT, TvKeys.DPAD_RIGHT)
            .forEach { code ->
                assertEquals(
                    "keyCode=$code",
                    TvKeyResult.Ignore,
                    routeTvKey(down(code), ctx(overlay = TvOverlay.CONTROLS)),
                )
            }
    }

    @Test
    fun `left closes the rail, up down navigate it`() {
        assertEquals(
            TvAction.CloseOverlay,
            perform(routeTvKey(down(TvKeys.DPAD_LEFT), ctx(overlay = TvOverlay.RAIL))),
        )
        assertEquals(
            TvKeyResult.Ignore,
            routeTvKey(down(TvKeys.DPAD_UP), ctx(overlay = TvOverlay.RAIL)),
        )
    }

    @Test
    fun `back closes the open overlay instead of exiting`() {
        assertEquals(
            TvAction.CloseOverlay,
            perform(routeTvKey(down(TvKeys.BACK), ctx(overlay = TvOverlay.RAIL))),
        )
    }

    // -- OSD numérico ---------------------------------------------------------

    @Test
    fun `center confirms digits while arrows are swallowed in osd`() {
        val osd = ctx(overlay = TvOverlay.NUMBER_OSD)
        assertEquals(TvAction.ConfirmDigits, perform(routeTvKey(down(TvKeys.DPAD_CENTER), osd)))
        assertEquals(TvKeyResult.Consume, routeTvKey(down(TvKeys.DPAD_UP), osd))
    }

    // -- ACTION_UP -------------------------------------------------------------

    @Test
    fun `key up is consumed when its down would perform`() {
        assertEquals(TvKeyResult.Consume, routeTvKey(up(TvKeys.DPAD_CENTER), ctx()))
    }

    @Test
    fun `center up propagates inside focusable overlay so click fires`() {
        assertEquals(
            TvKeyResult.Ignore,
            routeTvKey(up(TvKeys.DPAD_CENTER), ctx(overlay = TvOverlay.CONTROLS)),
        )
    }
}
