package com.iptv.feature.player.ui

import android.app.Activity
import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.mediarouter.app.MediaRouteChooserDialog
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.iptv.feature.player.R

@Composable
fun CastRouteButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    /** TV vinculada por el enlace de la app; si está activa, "transmitir"
     *  ofrece mandar a ella en lugar de ir sólo a Google Cast. */
    linkedTvName: String? = null,
    onSendToLinkedTv: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isCasting by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var showChooser by remember { mutableStateOf(false) }
    val linked = linkedTvName != null && onSendToLinkedTv != null

    val castContext = remember(activity) {
        runCatching { activity?.let { CastContext.getSharedInstance(it) } }.getOrNull()
    }

    val sessionManager = castContext?.sessionManager
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }

    val sessionListener = remember(sessionManager) {
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                isCasting = true
                isConnecting = false
            }
            override fun onSessionEnded(session: CastSession, error: Int) {
                isCasting = false
                isConnecting = false
            }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                isCasting = true
                isConnecting = false
            }
            override fun onSessionStarting(session: CastSession) { isConnecting = true }
            override fun onSessionStartFailed(session: CastSession, error: Int) { isConnecting = false }
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) { isConnecting = true }
            override fun onSessionResumeFailed(session: CastSession, error: Int) { isConnecting = false }
            override fun onSessionSuspended(session: CastSession, reason: Int) { isConnecting = false }
        }
    }

    DisposableEffect(sessionManager) {
        sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        isCasting = sessionManager?.currentCastSession != null
        onDispose { sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java) }
    }

    fun openCastChooser() {
        val act = activity ?: return
        val themedContext = ContextThemeWrapper(act, R.style.Theme_IPTVPlayer_CastButton)
        MediaRouteChooserDialog(themedContext).apply {
            setRouteSelector(selector)
            show()
        }
    }

    Box {
        IconButton(
            onClick = {
                when {
                    isCasting -> sessionManager?.endCurrentSession(true)
                    // Con una TV vinculada por la app, "transmitir" pregunta
                    // a dónde: la app de la TV o un receptor Google Cast.
                    linked -> showChooser = true
                    else -> openCastChooser()
                }
            },
            modifier = modifier,
            enabled = castContext != null || linked,
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Filled.Cast,
                    contentDescription = contentDescription,
                    tint = if (isCasting) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
        DropdownMenu(
            expanded = showChooser,
            onDismissRequest = { showChooser = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.player_send_to_linked_tv, linkedTvName.orEmpty()))
                },
                leadingIcon = { Icon(Icons.Filled.Tv, contentDescription = null) },
                onClick = {
                    showChooser = false
                    onSendToLinkedTv?.invoke()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.player_cast_google)) },
                leadingIcon = { Icon(Icons.Filled.Cast, contentDescription = null) },
                onClick = {
                    showChooser = false
                    openCastChooser()
                },
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
