package com.iptv.feature.tvlink.wire

import android.util.Log
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Envoltura mínima de un socket TCP: NDJSON (una línea = un [LinkMsg]).
 * La lectura es secuencial (un único bucle por extremo) y el envío está
 * serializado por un lock — sin canales ni actores.
 */
internal class LinkSocket(private val socket: Socket) {

    private val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
    private val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
    private val sendLock = Any()

    @Volatile var closed = false
        private set

    /**
     * Siguiente mensaje decodificable, o null si el extremo cerró. Las líneas
     * ilegibles (¿versión más nueva del protocolo?) se ignoran y se sigue
     * leyendo — antes una sola línea mala cortaba la conexión entera.
     */
    suspend fun read(): LinkMsg? = withContext(Dispatchers.IO) {
        var msg: LinkMsg? = null
        while (msg == null) {
            val line = runCatching { reader.readLine() }.getOrNull() ?: break
            msg = runCatching { linkJson.decodeFromString<LinkMsg>(line) }.getOrNull()
        }
        msg
    }

    fun send(msg: LinkMsg) {
        if (closed) return
        synchronized(sendLock) {
            runCatching {
                writer.write(linkJson.encodeToString(LinkMsg.serializer(), msg))
                writer.newLine()
                writer.flush()
            }.onFailure {
                Log.w(TAG, "send falló (${msg::class.simpleName}): ${it.message}")
            }
        }
    }

    fun close() {
        closed = true
        runCatching { socket.close() }
    }

    private companion object {
        const val TAG = "LinkSocket"
    }
}
