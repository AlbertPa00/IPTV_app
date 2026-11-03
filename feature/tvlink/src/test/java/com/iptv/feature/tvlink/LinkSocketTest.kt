package com.iptv.feature.tvlink

import com.iptv.feature.tvlink.wire.LinkMsg
import com.iptv.feature.tvlink.wire.LinkSocket
import com.iptv.feature.tvlink.wire.linkJson
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Transporte NDJSON sobre TCP real (loopback JVM). Cubre el contrato de
 * `LinkSocket.read()`: líneas ilegibles se saltan y el cierre del par se
 * reporta como null — el cliente depende de ello para reconectar.
 */
class LinkSocketTest {

    private class Pair(val link: LinkSocket, val rawPeer: Socket)

    private suspend fun connectPair(): Pair {
        val server = ServerSocket(0)
        val raw = Socket()
        val accepted = kotlinx.coroutines.withContext(Dispatchers.IO) {
            // accept() bloquea; el connect del par se dispara tras lanzarlo.
            java.util.concurrent.FutureTask { server.accept() }.also { t -> Thread(t).start() }.let {
                raw.connect(InetSocketAddress("127.0.0.1", server.localPort), 3_000)
                it.get(3, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
        server.close()
        return Pair(LinkSocket(accepted), raw)
    }

    @Test
    fun `round trip bidireccional`() = runBlocking {
        val a = connectPair()
        val peerLink = LinkSocket(a.rawPeer)

        val hello = LinkMsg.Hello(v = 1, deviceId = "d1", deviceName = "TV")
        a.link.send(hello)
        peerLink.send(LinkMsg.Pong)

        assertEquals(hello, withTimeout(3_000) { peerLink.read() })
        assertEquals(LinkMsg.Pong, withTimeout(3_000) { a.link.read() })
    }

    @Test
    fun `linea indecodificable se salta y la siguiente se entrega`() = runBlocking {
        val pair = connectPair()
        val out = pair.rawPeer.getOutputStream()
        // Basura entre mensajes válidos: el read() debe saltarla, no cortar.
        out.write("{\"t\":\"inexistente\"}\n".toByteArray(Charsets.UTF_8))
        out.write("no es json\n".toByteArray(Charsets.UTF_8))
        out.flush()
        out.write(
            (linkJson.encodeToString(LinkMsg.serializer(), LinkMsg.Ping) + "\n")
                .toByteArray(Charsets.UTF_8),
        )
        out.flush()

        assertEquals(LinkMsg.Ping, withTimeout(3_000) { pair.link.read() })
    }

    @Test
    fun `cierre del par devuelve null`() = runBlocking {
        val pair = connectPair()
        pair.rawPeer.close()
        assertNull(withTimeout(3_000) { pair.link.read() })
    }
}
