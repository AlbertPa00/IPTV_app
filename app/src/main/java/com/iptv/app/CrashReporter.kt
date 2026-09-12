package com.iptv.app

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Envoltorio seguro sobre Crashlytics: la app funciona igual sin
 * google-services.json (getInstance lanza y se ignora) y la recolección
 * está desactivada por defecto en el manifiesto hasta el opt-in del usuario.
 */
@Singleton
class CrashReporter @Inject constructor() {
    fun setEnabled(enabled: Boolean) {
        runCatching {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        }
    }
}
