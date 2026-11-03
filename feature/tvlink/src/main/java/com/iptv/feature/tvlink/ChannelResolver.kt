package com.iptv.feature.tvlink

import com.iptv.core.common.text.TextNormalizer
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.ChannelEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Traduce la identidad de un canal entre dispositivos. Los `channelId` de
 * Room son PK autoincrementales locales a cada base de datos: el mismo canal
 * en móvil y TV casi seguro tiene ids distintos — o peor, el mismo id puede
 * ser OTRO canal en el otro extremo. La identidad estable es `externalId`
 * (stream_id Xtream / URL M3U), con `providerNameNorm` como respaldo y el
 * id crudo sólo cuando el par no envía identidad (protocolo antiguo).
 */
@Singleton
internal class ChannelResolver @Inject constructor(
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
) {

    /** Clave + nombre a enviar al otro extremo para describir [channelId]. */
    suspend fun describe(channelId: Long): Pair<String?, String?> {
        val ch = channelDao.findById(channelId) ?: return null to null
        return ch.externalId to ch.providerName
    }

    /**
     * Canal local equivalente al referido por el otro extremo: primero por
     * clave externa (en la fuente activa, luego en cualquier fuente), luego
     * por nombre de proveedor; si el par no mandó identidad, el id tal cual.
     */
    suspend fun resolve(channelId: Long, key: String?, name: String?): ChannelEntity? {
        if (!key.isNullOrBlank()) {
            sourceDao.findActive()?.id
                ?.let { sid -> channelDao.findByExternalId(sid, key) }
                ?.let { return it }
            channelDao.findByExternalIdAnySource(key)?.let { return it }
        }
        if (!name.isNullOrBlank()) {
            val norm = TextNormalizer.searchKey(name)
            sourceDao.findActive()?.id
                ?.let { sid -> channelDao.findByProviderNameNorm(sid, norm) }
                ?.let { return it }
            channelDao.findByProviderNameNormAny(norm)?.let { return it }
        }
        // Con identidad enviada pero sin resolución, NO caer al id crudo:
        // en otra base de datos ese id probablemente es otro canal — mejor
        // no hacer nada que reproducir el canal equivocado.
        return if (key.isNullOrBlank() && name.isNullOrBlank()) {
            channelDao.findById(channelId)
        } else {
            null
        }
    }
}
