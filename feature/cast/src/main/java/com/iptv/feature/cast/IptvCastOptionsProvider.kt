package com.iptv.feature.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Cast SDK entry point. The MVP intentionally uses Google's Default Media Receiver. */
class IptvCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions = CastOptions.Builder()
        .setReceiverApplicationId(DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        // La notificación propia de CastProxyService ya cubre el control remoto:
        // la capa de media session/notificación del SDK es redundante y su
        // servicio es una fuente conocida de RemoteServiceException/NPE al
        // cerrar la sesión. El token de la notificación propia viene de la
        // sesión Media3 local, no de esta capa.
        .setCastMediaOptions(
            CastMediaOptions.Builder()
                .setMediaSessionEnabled(false)
                .build(),
        )
        .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
