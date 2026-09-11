package com.iptv.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.iptv.core.common.pip.PipController
import com.iptv.core.designsystem.theme.IptvTheme
import com.iptv.feature.player.cast.CastVolumeKeyHandler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pipController: PipController
    @Inject lateinit var castVolumeKeys: CastVolumeKeyHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IptvTheme {
                IptvApp()
            }
        }
    }

    /** Si el reproductor está activo, salir con Home minimiza a PiP. */
    override fun onUserLeaveHint() {
        if (pipController.autoEnterOnUserLeave) enterPip()
        super.onUserLeaveHint()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    /** Con una sesión Cast activa, las teclas de volumen controlan la TV. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        castVolumeKeys.handle(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        castVolumeKeys.handle(event) || super.onKeyUp(keyCode, event)

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipController.onPipModeChanged(isInPictureInPictureMode)
    }
}
