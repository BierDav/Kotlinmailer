package at.quickme.kotlinmailer.smtp

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.network.tls.TLSConfigBuilder
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/** Whether ktor-network-tls supports this platform. */
internal expect val ktorTlsSupported: Boolean

/**
 * [SmtpSocketFactory] based on ktor-network, with TLS from ktor-network-tls.
 *
 * @param tls customizes the TLS handshake, e.g. a custom `trustManager` on the JVM
 */
public class KtorSmtpSocketFactory(private val tls: TLSConfigBuilder.() -> Unit = {}) : SmtpSocketFactory {
    @Suppress("TooGenericExceptionCaught") // close the socket on any failure
    override suspend fun connect(host: String, port: Int, implicitTls: Boolean, timeout: Duration): SmtpSocket {
        val context = SupervisorJob() + Dispatchers.IO
        val selector = SelectorManager(context)
        try {
            val socket = withTimeout(timeout) { aSocket(selector).tcp().connect(host, port) }
            return KtorSmtpSocket(host, socket.connection(), selector, tls).apply {
                if (implicitTls) withTimeout(timeout) { startTls() }
            }
        } catch (e: Throwable) {
            selector.close()
            context.cancel()
            throw e
        }
    }
}

private class KtorSmtpSocket(
    private val host: String,
    private var connection: Connection,
    private val selector: SelectorManager,
    private val tlsConfig: TLSConfigBuilder.() -> Unit,
) : SmtpSocket {
    private var socket: Socket = connection.socket
    private var input: ByteReadChannel = connection.input
    private var output: ByteWriteChannel = connection.output
    private val lines = LineBuffer()
    private val chunk = ByteArray(CHUNK_SIZE)

    override var isSecure: Boolean = false
        private set

    override suspend fun readLine(): String? {
        while (true) {
            lines.pollLine()?.let { return it }
            val read = input.readAvailable(chunk)
            if (read < 0) return null
            lines.append(chunk, 0, read)
        }
    }

    override suspend fun write(data: ByteArray) = output.writeFully(data)

    override suspend fun flush() = output.flush()

    override suspend fun startTls() {
        if (!ktorTlsSupported) {
            throw SmtpException(
                "TLS is not available on this platform; " +
                    "use SmtpSecurity.NONE for a trusted relay or a custom SmtpSocketFactory",
            )
        }
        // data received before the handshake could have been injected by an attacker (CVE-2011-0411)
        if (!lines.isEmpty()) throw SmtpException("Server sent unexpected data before the TLS handshake")
        socket = connection.tls(selector.coroutineContext) {
            serverName = host
            tlsConfig()
        }
        connection = socket.connection()
        input = connection.input
        output = connection.output
        isSecure = true
    }

    override fun close() {
        socket.close()
        selector.close()
        selector.coroutineContext.cancel()
    }

    private companion object {
        const val CHUNK_SIZE = 8192
    }
}
