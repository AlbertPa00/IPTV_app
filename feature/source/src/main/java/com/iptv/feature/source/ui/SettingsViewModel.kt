package com.iptv.feature.source.ui

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.core.common.download.DownloadController
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.common.sync.CatalogSyncScheduler
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.DownloadDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.core.storage.entity.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ajustes de la app: refresco automático (vía [CatalogSyncScheduler]),
 * control parental con PIN y gestión de categorías bloqueadas.
 *
 * Modelo: una categoría bloqueada se oculta siempre; el PIN sólo protege la
 * gestión (activar, desactivar y cambiar qué está bloqueado).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val scheduler: CatalogSyncScheduler,
    sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val downloadDao: DownloadDao,
    private val downloadController: DownloadController,
) : ViewModel() {

    /** Diálogo de PIN pendiente; el propósito decide qué pasa al acertar. */
    enum class PinPrompt { CREATE, CONFIRM_CREATE, DISABLE, MANAGE }

    data class UiState(
        val autoRefresh: Boolean = true,
        val wifiOnly: Boolean = true,
        val downloadsWifiOnly: Boolean = true,
        val crashReporting: Boolean = false,
        val parentalEnabled: Boolean = false,
        val bufferProfile: String = AppPreferences.BUFFER_AUTO,
        val liveReconnectContinuous: Boolean = false,
        val categories: List<CategoryEntity> = emptyList(),
        val pinPrompt: PinPrompt? = null,
        val pinError: Boolean = false,
        val managingCategories: Boolean = false,
    )

    private val pinPrompt = MutableStateFlow<PinPrompt?>(null)
    private val pinError = MutableStateFlow(false)
    private val managingCategories = MutableStateFlow(false)
    private val pinDraft = MutableStateFlow<String?>(null)

    // replay = MAX: al volver a la pestaña se re-emite el último valor sin
    // parpadeo a vacío; el upstream igualmente descansa a los 5 s sin UI.
    private val categories = sourceDao.observeActive()
        .flatMapLatest { source ->
            if (source == null) flowOf(emptyList())
            else categoryDao.observeAllBySource(source.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), emptyList())

    private val activeSource = sourceDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), null)

    private data class SyncPrefs(
        val autoRefresh: Boolean,
        val wifiOnly: Boolean,
        val crashReporting: Boolean,
        val downloadsWifiOnly: Boolean,
    )

    private val syncPrefs = combine(
        prefs.autoRefreshEnabled,
        prefs.autoRefreshWifiOnly,
        prefs.crashReportingEnabled,
        prefs.downloadsWifiOnly,
        ::SyncPrefs,
    )

    private data class PlaybackPrefs(
        val bufferProfile: String,
        val liveReconnectContinuous: Boolean,
    )

    private val playbackPrefs = combine(
        prefs.bufferProfile,
        prefs.liveReconnectContinuous,
        ::PlaybackPrefs,
    )

    val uiState: StateFlow<UiState> = combine(
        syncPrefs,
        playbackPrefs,
        prefs.hasPin,
        categories,
        combine(pinPrompt, pinError, managingCategories) { p, e, m -> Triple(p, e, m) },
    ) { sync, playback, hasPin, cats, (prompt, error, managing) ->
        UiState(
            autoRefresh = sync.autoRefresh,
            wifiOnly = sync.wifiOnly,
            downloadsWifiOnly = sync.downloadsWifiOnly,
            crashReporting = sync.crashReporting,
            parentalEnabled = hasPin,
            bufferProfile = playback.bufferProfile,
            liveReconnectContinuous = playback.liveReconnectContinuous,
            categories = cats,
            pinPrompt = prompt,
            pinError = error,
            managingCategories = managing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), UiState())

    // -- Descargas -----------------------------------------------------------

    /** Estado de la sección Descargas de Ajustes: lista, tamaños y confirmaciones. */
    data class DownloadsUi(
        val items: List<DownloadEntity> = emptyList(),
        val usedBytes: Long = 0,
        val freeBytes: Long = 0,
        val pendingDelete: DownloadEntity? = null,
        val confirmDeleteAll: Boolean = false,
    )

    private val pendingDownloadDelete = MutableStateFlow<DownloadEntity?>(null)
    private val pendingDeleteAllDownloads = MutableStateFlow(false)

    // Los tamaños se leen de disco: fichero real si la descarga terminó,
    // bytes del parcial si sigue en curso. StatFs da el espacio libre del
    // volumen donde vive filesDir (hoy, almacenamiento interno).
    private val downloadsWithStats = downloadDao.observeAll()
        .map { list ->
            val used = list.sumOf { download ->
                when {
                    download.status == DownloadStatus.DONE ->
                        download.localPath
                            ?.let { File(it).length().takeIf { size -> size > 0 } }
                            ?: download.totalBytes

                    else -> download.downloadedBytes
                }
            }
            Triple(list, used, StatFs(context.filesDir.path).availableBytes)
        }
        .flowOn(Dispatchers.IO)

    val downloadsUi: StateFlow<DownloadsUi> = combine(
        downloadsWithStats,
        pendingDownloadDelete,
        pendingDeleteAllDownloads,
    ) { (items, used, free), pending, deleteAll ->
        DownloadsUi(
            items = items,
            usedBytes = used,
            freeBytes = free,
            pendingDelete = pending,
            confirmDeleteAll = deleteAll,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), DownloadsUi())

    fun requestDownloadDelete(download: DownloadEntity) {
        pendingDownloadDelete.value = download
    }

    fun dismissDownloadDelete() {
        pendingDownloadDelete.value = null
    }

    fun confirmDownloadDelete() {
        val target = pendingDownloadDelete.value ?: return
        pendingDownloadDelete.value = null
        viewModelScope.launch { downloadController.delete(target.id) }
    }

    fun requestDeleteAllDownloads() {
        if (downloadsUi.value.items.isNotEmpty()) pendingDeleteAllDownloads.value = true
    }

    fun dismissDeleteAllDownloads() {
        pendingDeleteAllDownloads.value = false
    }

    fun confirmDeleteAllDownloads() {
        pendingDeleteAllDownloads.value = false
        viewModelScope.launch { downloadController.removeAll() }
    }

    fun setAutoRefresh(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAutoRefresh(enabled)
            scheduler.apply(enabled, prefs.isAutoRefreshWifiOnly())
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            prefs.setAutoRefreshWifiOnly(wifiOnly)
            scheduler.apply(prefs.isAutoRefreshEnabled(), wifiOnly)
        }
    }

    /** Las descargas de vídeo pesan GBs: por defecto sólo con Wi-Fi. */
    fun setDownloadsWifiOnly(wifiOnly: Boolean) {
        prefs.setDownloadsWifiOnly(wifiOnly)
    }

    fun setCrashReporting(enabled: Boolean) {
        prefs.setCrashReporting(enabled)
    }

    // -- Reproducción ---------------------------------------------------------

    /** Perfil de búfer: "auto", "low" (arranque rápido) u "high" (inestable). */
    fun setBufferProfile(profile: String) {
        prefs.setBufferProfile(profile)
    }

    /** En directo: reintentar sin límite cuando un canal se corta. */
    fun setLiveReconnectContinuous(continuous: Boolean) {
        prefs.setLiveReconnectContinuous(continuous)
    }

    /** Entrada del usuario al flujo de control parental. */
    fun onParentalToggleClick() {
        pinPrompt.value = if (prefs.hasPinSet()) PinPrompt.DISABLE else PinPrompt.CREATE
        pinError.value = false
    }

    fun onManageCategoriesClick() {
        if (managingCategories.value) {
            managingCategories.value = false
            return
        }
        // El PIN sólo protege si existe; ocultar/reordenar no es sensible.
        if (prefs.hasPinSet()) {
            pinPrompt.value = PinPrompt.MANAGE
            pinError.value = false
        } else {
            managingCategories.value = true
        }
    }

    fun dismissPin() {
        pinPrompt.value = null
        pinError.value = false
        pinDraft.value = null
    }

    /** Envío del PIN introducido; decide según [PinPrompt]. */
    fun submitPin(pin: String) {
        when (pinPrompt.value) {
            PinPrompt.CREATE -> {
                pinDraft.value = pin
                pinPrompt.value = PinPrompt.CONFIRM_CREATE
                pinError.value = false
            }

            PinPrompt.CONFIRM_CREATE -> {
                if (pinDraft.value == pin) {
                    prefs.setPin(pin)
                    pinPrompt.value = null
                    pinDraft.value = null
                    autoLockAdultCategories()
                } else {
                    pinPrompt.value = PinPrompt.CREATE
                    pinDraft.value = null
                    pinError.value = true
                }
            }

            PinPrompt.DISABLE -> if (verifyOrFail(pin)) disableParental()

            PinPrompt.MANAGE -> if (verifyOrFail(pin)) managingCategories.value = true

            null -> Unit
        }
    }

    private fun verifyOrFail(pin: String): Boolean {
        val ok = prefs.verifyPin(pin)
        pinError.value = !ok
        if (ok) pinPrompt.value = null
        return ok
    }

    private fun disableParental() {
        prefs.clearPin()
        managingCategories.value = false
        viewModelScope.launch {
            activeSource.value?.let { categoryDao.unlockAll(it.id) }
        }
    }

    /** Al crear el PIN se bloquean de entrada las categorías "de adultos". */
    private fun autoLockAdultCategories() {
        viewModelScope.launch {
            categories.value
                .filter { ADULT_CATEGORY_REGEX.containsMatchIn(it.name) }
                .forEach { categoryDao.setLocked(it.id, true) }
        }
    }

    fun setCategoryLocked(categoryId: Long, locked: Boolean) {
        viewModelScope.launch { categoryDao.setLocked(categoryId, locked) }
    }

    fun setCategoryHidden(categoryId: Long, hidden: Boolean) {
        viewModelScope.launch { categoryDao.setHidden(categoryId, hidden) }
    }

    /**
     * Mueve la categoría una posición dentro de su grupo (LIVE/VOD/SERIES).
     * Reescribe el sortOrder del grupo completo según el orden visible.
     */
    fun moveCategory(categoryId: Long, up: Boolean) {
        val list = categories.value
        val index = list.indexOfFirst { it.id == categoryId }
        if (index < 0) return
        val kind = list[index].kind
        val group = list.filter { it.kind == kind }.toMutableList()
        val from = group.indexOfFirst { it.id == categoryId }
        val to = if (up) from - 1 else from + 1
        if (to !in group.indices) return
        group[from] = group[to].also { group[to] = group[from] }
        val reordered = group.mapIndexed { i, category -> category.copy(sortOrder = i) }
        viewModelScope.launch { categoryDao.updateAll(reordered) }
    }

    fun closeCategories() {
        managingCategories.value = false
    }

    private companion object {
        val ADULT_CATEGORY_REGEX = Regex(
            "xxx|adult|adultos|porn|erotic|erótico|18\\+|\\+18|\\bsex\\b|hustler|playboy|venus|hot ?club|only ?fans",
            RegexOption.IGNORE_CASE,
        )
    }
}
