package at.quickme.kotlinmailer.smtp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.time.Duration

private external fun require(module: String): dynamic

private fun isNode(): Boolean =
    js("typeof process !== 'undefined' && process.versions != null && process.versions.node != null") as Boolean

public actual fun defaultSmtpSocketFactory(): SmtpSocketFactory = NodeSmtpSocketFactory()

/**
 * [SmtpSocketFactory] based on the Node.js `net` and `tls` modules. Not available in browsers.
 *
 * @param tlsOptions additional options passed to `tls.connect`, e.g. `ca`
 */
public class NodeSmtpSocketFactory(private val tlsOptions: dynamic = null) : SmtpSocketFactory {
    @Suppress("TooGenericExceptionCaught") // close the socket on any failure
    override suspend fun connect(host: String, port: Int, implicitTls: Boolean, timeout: Duration): SmtpSocket {
        check(isNode()) {
            "SMTP needs raw sockets, which are only available on Node.js; use JMAP in browsers"
        }
        val socket = NodeSmtpSocket(host, tlsOptions)
        try {
            withTimeout(timeout) { socket.connect(port, implicitTls) }
        } catch (e: Throwable) {
            socket.close()
            throw e
        }
        return socket
    }
}

private class NodeSmtpSocket(private val host: String, private val tlsOptions: dynamic) : SmtpSocket {
    private val incoming = Channel<Any>(Channel.UNLIMITED)
    private val lines = LineBuffer()
    private var socket: dynamic = null
    private var pending = mutableListOf<ByteArray>()

    override var isSecure: Boolean = false
        private set

    suspend fun connect(port: Int, implicitTls: Boolean) {
        val connected = CompletableDeferred<Unit>()
        socket = if (implicitTls) {
            require("tls").connect(options(port = port), { connected.complete(Unit) })
        } else {
            require("net").connect(port, host, { connected.complete(Unit) })
        }
        socket.once("error") { error: dynamic -> connected.completeExceptionally(SmtpException("${error.message}")) }
        connected.await()
        isSecure = implicitTls
        listen()
    }

    override suspend fun readLine(): String? {
        while (true) {
            lines.pollLine()?.let { return it }
            when (val next = incoming.receiveCatching().getOrNull() ?: return null) {
                is ByteArray -> lines.append(next)
                is Throwable -> throw next
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        pending += data
    }

    override suspend fun flush() {
        if (pending.isEmpty()) return
        val buffer = js("Buffer").concat(pending.map { toBuffer(it) }.toTypedArray())
        pending = mutableListOf()
        val written = CompletableDeferred<Unit>()
        socket.write(buffer) { error: dynamic ->
            if (error == null || error == undefined) written.complete(Unit) else {
                written.completeExceptionally(SmtpException("${error.message}"))
            }
        }
        written.await()
    }

    override suspend fun startTls() {
        // data received before the handshake could have been injected by an attacker (CVE-2011-0411)
        if (!lines.isEmpty()) throw SmtpException("Server sent unexpected data before the TLS handshake")
        val plain = socket
        plain.removeAllListeners("data")
        plain.removeAllListeners("error")
        plain.removeAllListeners("close")
        val secured = CompletableDeferred<Unit>()
        val options = options(socket = plain)
        socket = require("tls").connect(options, { secured.complete(Unit) })
        socket.once("error") { error: dynamic -> secured.completeExceptionally(SmtpException("${error.message}")) }
        secured.await()
        isSecure = true
        listen()
    }

    override fun close() {
        socket?.destroy()
        incoming.close()
    }

    private fun listen() {
        socket.removeAllListeners("error")
        socket.on("data") { data: dynamic ->
            val bytes = Int8Array(data.buffer, data.byteOffset, data.length).unsafeCast<ByteArray>()
            incoming.trySend(bytes.copyOf())
        }
        socket.on("error") { error: dynamic -> incoming.trySend(SmtpException("Connection failed: ${error.message}")) }
        socket.on("close") { _: dynamic -> incoming.close() }
    }

    private fun options(port: Int? = null, socket: dynamic = null): dynamic {
        val options: dynamic = js("({})")
        if (tlsOptions != null) js("Object").assign(options, tlsOptions)
        options.host = host
        options.servername = host
        if (port != null) options.port = port
        if (socket != null) options.socket = socket
        return options
    }

    private fun toBuffer(bytes: ByteArray): dynamic {
        val array = bytes.unsafeCast<Int8Array>()
        return js("Buffer").from(Uint8Array(array.buffer, array.byteOffset, array.length))
    }
}
