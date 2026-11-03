package com.iptv.feature.tvlink.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.core.common.playback.PlaybackSession
import com.iptv.core.common.tvlink.DiscoveredTv
import com.iptv.core.common.tvlink.PairedTv
import com.iptv.core.common.tvlink.TvLinkClient
import com.iptv.core.common.tvlink.TvLinkHost
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.SourceEntity
import com.iptv.core.storage.entity.SourceTypes
import com.iptv.core.storage.security.CredentialCrypto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Adaptador de UI de [TvLinkHost] + [TvLinkClient]: las pantallas consumen
 * los flujos de los contratos directamente; el ViewModel sólo arranca y
 * para el descubrimiento con su ciclo de vida y traduce acciones.
 */
@HiltViewModel
class TvLinkViewModel @Inject constructor(
    private val host: TvLinkHost,
    private val client: TvLinkClient,
    private val playback: PlaybackSession,
    private val sourceDao: SourceDao,
    private val credentialCrypto: CredentialCrypto,
) : ViewModel() {

    val hostStatus = host.status
    val discovered = client.discovered
    val pairedTvs = client.pairedTvs
    val connection = client.connection
    val remote = client.remote
    val localNowPlaying = playback.nowPlaying
    val handoffApplied = client.handoffApplied

    /** Fuentes configuradas en ESTE dispositivo (para "sincronizar mi lista"). */
    val localSources = sourceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _syncing = MutableStateFlow(false)
    val syncingSources = _syncing.asStateFlow()

    init {
        client.startDiscovery()
    }

    override fun onCleared() {
        client.stopDiscovery()
    }

    fun setHostEnabled(enabled: Boolean) = host.setEnabled(enabled)
    fun showPin() = host.showPairingPin()
    fun hidePin() = host.hidePairingPin()
    fun revoke(clientId: String) = host.revokeClient(clientId)

    fun connect(tv: DiscoveredTv) = client.connect(tv.host, tv.port, tv.id, tv.name)
    fun connect(tv: PairedTv) {
        tv.host?.let { client.connect(it, tv.port, tv.id, tv.name) }
    }
    fun connectManual(host: String, port: Int) = client.connect(host.trim(), port)
    fun submitPin(pin: String) = client.submitPin(pin)
    fun disconnect() = client.disconnect()
    fun unpair(deviceId: String) = client.unpair(deviceId)

    /** Handoff móvil → TV: lo que suena aquí pasa a reproducirse allá. */
    fun sendLocalToTv() {
        val now = playback.nowPlaying.value ?: return
        val position = if (now.isLive) null else playback.player.value?.currentPosition
        remote.value?.play(now.channelId, position)
    }

    /** Handoff TV → este dispositivo (la navegación llega vía handoffApplied). */
    fun takeHere() = remote.value?.takeToThisDevice()

    /**
     * "Sincronizar mi lista": empuja las fuentes locales elegidas (M3U por
     * URL y Xtream — los ficheros M3U son locales del teléfono y no
     * aplican) al host, una a una esperando el fin de cada operación. El
     * alta en el host es idempotente (`ensure*Source` reutiliza la fuente
     * existente), así que repetir la sincronización no duplica nada.
     */
    fun syncMySources(sources: List<SourceEntity>? = null) {
        val session = remote.value ?: return
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                val mine = sources ?: sourceDao.observeAll().first()
                for (s in mine) {
                    val opId = when (s.type) {
                        SourceTypes.XTREAM -> {
                            val pass = s.passwordEnc?.let(credentialCrypto::decrypt)
                            val host = s.host
                            val user = s.username
                            if (pass == null || host == null || user == null) {
                                null
                            } else {
                                session.addXtreamSource(
                                    s.name,
                                    s.port?.let { "$host:$it" } ?: host,
                                    user,
                                    pass,
                                )
                            }
                        }
                        SourceTypes.M3U_URL -> s.url?.let { session.addM3uSource(s.name, it) }
                        else -> null // M3U_FILE: el fichero es local del móvil
                    } ?: continue
                    withTimeoutOrNull(SYNC_OP_TIMEOUT_MS) {
                        session.sourceOp.first { it?.opId == opId && !it.running }
                    }
                }
                session.requestSources()
            } finally {
                _syncing.value = false
            }
        }
    }

    private companion object {
        const val SYNC_OP_TIMEOUT_MS = 10 * 60_000L
    }
}
