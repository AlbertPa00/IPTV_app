package com.iptv.core.common.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/**
 * Preferencias de la app (SharedPreferences): control parental (PIN),
 * refresco automático y ajustes sencillos de un solo usuario.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -- Refresco automático -------------------------------------------------

    val autoRefreshEnabled: Flow<Boolean> = booleanFlow(KEY_AUTO_REFRESH, default = true)
    val autoRefreshWifiOnly: Flow<Boolean> = booleanFlow(KEY_WIFI_ONLY, default = true)

    suspend fun setAutoRefresh(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
    }

    suspend fun setAutoRefreshWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
    }

    fun isAutoRefreshEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_REFRESH, true)
    fun isAutoRefreshWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, true)

    // -- Informes de fallos (opt-in) -----------------------------------------

    val crashReportingEnabled: Flow<Boolean> = booleanFlow(KEY_CRASH_REPORTING, default = false)

    fun setCrashReporting(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CRASH_REPORTING, enabled).apply()
    }

    // -- Control parental (PIN) ----------------------------------------------

    val hasPin: Flow<Boolean> = stringFlow(KEY_PIN_HASH).map { !it.isNullOrBlank() }

    fun hasPinSet(): Boolean = !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank()

    /** Define o cambia el PIN. El PIN nunca se guarda en claro. */
    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes).toHex()
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash(salt, pin))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return expected == hash(salt, pin)
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
    }

    // -- Internals ------------------------------------------------------------

    private fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = callbackFlow {
        trySend(prefs.getBoolean(key, default))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) trySend(prefs.getBoolean(key, default))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun stringFlow(key: String): Flow<String?> = callbackFlow {
        trySend(prefs.getString(key, null))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key) trySend(prefs.getString(key, null))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun hash(saltHex: String, pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((saltHex + ":" + pin).toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val PREFS_NAME = "iptv_prefs"
        const val KEY_AUTO_REFRESH = "auto_refresh_enabled"
        const val KEY_WIFI_ONLY = "auto_refresh_wifi_only"
        const val KEY_CRASH_REPORTING = "crash_reporting_enabled"
        const val KEY_PIN_SALT = "parental_pin_salt"
        const val KEY_PIN_HASH = "parental_pin_hash"
        const val SALT_BYTES = 16
    }
}
