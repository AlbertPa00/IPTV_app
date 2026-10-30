package com.iptv.feature.player.tv

/**
 * Modelo de entrada de TV desacoplado del framework: la pantalla traduce
 * el KeyEvent de Android a [TvKey] y el router decide la acción. Al no
 * importar android.* todo esto corre en JUnit puro.
 */
enum class TvKeyAction { DOWN, UP }

data class TvKey(
    val code: Int,
    val action: TvKeyAction,
    val repeat: Int = 0,
)

/** Keycodes usados (valores android.view.KeyEvent; duplicados a propósito
 *  para no importar android.* en el modelo). */
object TvKeys {
    const val BACK = 4
    const val DIGIT_0 = 7
    const val DIGIT_9 = 16
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val NUMPAD_ENTER = 160
    const val ENTER = 66
    const val MENU = 82
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val MEDIA_PLAY = 126
    const val MEDIA_PAUSE = 127
    const val INFO = 165
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val GUIDE = 172
}

/** Overlay visible sobre el vídeo permanente. Uno sólo a la vez. */
enum class TvOverlay {
    /** Sólo el vídeo; cualquier tecla "despierta" la UI. */
    NONE,
    /** Barra de transporte inferior con botones enfocables. */
    CONTROLS,
    /** Panel lateral de canales para zapear. */
    RAIL,
    /** Tarjeta informativa del canal (post-zap o tecla INFO). */
    INFO,
    /** Panel EPG "ahora y a continuación" (tecla GUIDE). */
    GUIDE,
    /** Entrada directa de número de canal. */
    NUMBER_OSD,
}

/** Contexto que el router necesita para decidir. */
data class TvInputContext(
    val overlay: TvOverlay,
    val isLive: Boolean,
    val hasChannelList: Boolean,
)

sealed interface TvAction {
    data object OpenControls : TvAction
    data object CloseOverlay : TvAction
    data object OpenRail : TvAction
    data object OpenInfo : TvAction
    data object OpenGuide : TvAction
    data object TogglePlayPause : TvAction
    data class SeekBy(val deltaMs: Long) : TvAction
    data class ZapBy(val delta: Int) : TvAction
    data class Digit(val n: Int) : TvAction
    data object ConfirmDigits : TvAction

    /** Salir de la app/pantalla: lo decide el nivel de navegación. */
    data object Back : TvAction
}

/** Resultado del enrutado: acción a ejecutar, tecla consumida sin acción
 *  o propagación al sistema de foco/click normal. */
sealed interface TvKeyResult {
    data class Perform(val action: TvAction) : TvKeyResult
    data object Consume : TvKeyResult
    data object Ignore : TvKeyResult
}

private const val SEEK_FAST_MS = 30_000L

/**
 * Enruta una tecla a un [TvKeyResult] según el overlay visible.
 *
 * Reglas:
 * - Sin overlay: las flechas ejecutan acciones directas (zap, rail, info)
 *   porque no hay nada enfocable a lo que navegar.
 * - Con overlay enfocable (CONTROLS/RAIL): las flechas se ignoran y el
 *   foco/click nativo opera dentro; BACK/LEFT cierran.
 * - Las teclas de función (media, dígitos, info, guía) actúan en
 *   cualquier estado.
 * - En ACTION_UP se consume la tecla sólo si su DOWN, en el contexto
 *   actual, sería nuestra: así el CENTER sobre un botón enfocado llega a
 *   ser click mientras que el CENTER que abrió los controles no se
 *   propaga como click fantasma.
 */
fun routeTvKey(key: TvKey, ctx: TvInputContext): TvKeyResult {
    if (key.action == TvKeyAction.UP) {
        val downResult = routeTvKeyDown(TvKey(key.code, TvKeyAction.DOWN, key.repeat), ctx)
        return if (downResult != TvKeyResult.Ignore) TvKeyResult.Consume else TvKeyResult.Ignore
    }
    return routeTvKeyDown(key, ctx)
}

private fun routeTvKeyDown(key: TvKey, ctx: TvInputContext): TvKeyResult {
    val digits = key.code in TvKeys.DIGIT_0..TvKeys.DIGIT_9
    if (digits) {
        // Sin lista numerada (VOD) los dígitos no resuelven a nada.
        return if (ctx.hasChannelList) {
            TvKeyResult.Perform(TvAction.Digit(key.code - TvKeys.DIGIT_0))
        } else {
            TvKeyResult.Ignore
        }
    }

    return when (key.code) {
        TvKeys.MEDIA_PLAY_PAUSE, TvKeys.MEDIA_PLAY, TvKeys.MEDIA_PAUSE ->
            TvKeyResult.Perform(TvAction.TogglePlayPause)

        TvKeys.MEDIA_REWIND -> seekOrIgnore(ctx, -SEEK_FAST_MS)
        TvKeys.MEDIA_FAST_FORWARD -> seekOrIgnore(ctx, SEEK_FAST_MS)

        TvKeys.CHANNEL_UP -> zapOrIgnore(ctx, +1)
        TvKeys.CHANNEL_DOWN -> zapOrIgnore(ctx, -1)

        TvKeys.INFO -> TvKeyResult.Perform(TvAction.OpenInfo)
        // La tecla GUIDE funciona como conmutador del panel EPG.
        TvKeys.GUIDE -> TvKeyResult.Perform(
            if (ctx.overlay == TvOverlay.GUIDE) TvAction.CloseOverlay else TvAction.OpenGuide,
        )
        TvKeys.MENU -> TvKeyResult.Perform(TvAction.OpenControls)

        TvKeys.BACK -> TvKeyResult.Perform(
            if (ctx.overlay != TvOverlay.NONE) TvAction.CloseOverlay else TvAction.Back,
        )

        TvKeys.DPAD_CENTER, TvKeys.ENTER, TvKeys.NUMPAD_ENTER -> when (ctx.overlay) {
            TvOverlay.NONE -> TvKeyResult.Perform(TvAction.OpenControls)
            TvOverlay.NUMBER_OSD -> TvKeyResult.Perform(TvAction.ConfirmDigits)
            // Con overlay enfocable el CENTER es un click: lo gestiona el
            // sistema de foco.
            else -> TvKeyResult.Ignore
        }

        TvKeys.DPAD_UP -> when (ctx.overlay) {
            TvOverlay.NONE, TvOverlay.INFO -> zapOrIgnore(ctx, +1)
            TvOverlay.NUMBER_OSD -> TvKeyResult.Consume
            else -> TvKeyResult.Ignore
        }

        TvKeys.DPAD_DOWN -> when (ctx.overlay) {
            TvOverlay.NONE, TvOverlay.INFO -> zapOrIgnore(ctx, -1)
            TvOverlay.NUMBER_OSD -> TvKeyResult.Consume
            else -> TvKeyResult.Ignore
        }

        TvKeys.DPAD_LEFT -> when (ctx.overlay) {
            TvOverlay.NONE, TvOverlay.INFO -> TvKeyResult.Perform(TvAction.OpenRail)
            TvOverlay.RAIL -> TvKeyResult.Perform(TvAction.CloseOverlay)
            TvOverlay.NUMBER_OSD -> TvKeyResult.Consume
            else -> TvKeyResult.Ignore
        }

        TvKeys.DPAD_RIGHT -> when (ctx.overlay) {
            TvOverlay.NONE, TvOverlay.INFO -> TvKeyResult.Perform(TvAction.OpenInfo)
            TvOverlay.RAIL -> TvKeyResult.Consume
            TvOverlay.NUMBER_OSD -> TvKeyResult.Consume
            else -> TvKeyResult.Ignore
        }

        else -> TvKeyResult.Ignore
    }
}

private fun seekOrIgnore(ctx: TvInputContext, deltaMs: Long): TvKeyResult =
    if (ctx.isLive) TvKeyResult.Ignore else TvKeyResult.Perform(TvAction.SeekBy(deltaMs))

private fun zapOrIgnore(ctx: TvInputContext, delta: Int): TvKeyResult =
    if (!ctx.hasChannelList) TvKeyResult.Ignore else TvKeyResult.Perform(TvAction.ZapBy(delta))
