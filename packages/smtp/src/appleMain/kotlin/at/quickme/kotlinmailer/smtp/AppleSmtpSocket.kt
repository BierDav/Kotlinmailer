package at.quickme.kotlinmailer.smtp

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSInputStream
import platform.Foundation.NSOutputStream
import platform.Foundation.NSStream
import platform.Foundation.NSStreamSocketSecurityLevelKey
import platform.Foundation.NSStreamSocketSecurityLevelNegotiatedSSL
import platform.Foundation.NSStreamStatusAtEnd
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusError
import platform.Foundation.NSStreamStatusNotOpen
import platform.Foundation.NSStreamStatusOpening
import platform.Foundation.getStreamsToHostWithName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public actual fun defaultSmtpSocketFactory(): SmtpSocketFactory = AppleSmtpSocketFactory

/**
 * [SmtpSocketFactory] based on Foundation streams, using the system's TLS stack and trust store.
 */
public object AppleSmtpSocketFactory : SmtpSocketFactory {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    @Suppress("TooGenericExceptionCaught") // close the socket on any failure
    override suspend fun connect(host: String, port: Int, implicitTls: Boolean, timeout: Duration): SmtpSocket {
        val (input, output) = memScoped {
            val input = alloc<ObjCObjectVar<NSInputStream?>>()
            val output = alloc<ObjCObjectVar<NSOutputStream?>>()
            NSStream.getStreamsToHostWithName(host, port.toLong(), input.ptr, output.ptr)
            checkNotNull(input.value) to checkNotNull(output.value)
        }
        val socket = AppleSmtpSocket(input, output)
        try {
            if (implicitTls) socket.enableTls()
            input.open()
            output.open()
            withTimeout(timeout) { socket.awaitOpen() }
        } catch (e: Throwable) {
            socket.close()
            throw e
        }
        return socket
    }
}

/**
 * Foundation streams are blocking, so they are polled with suspending delays instead,
 * which keeps reads cancellable and lets timeouts work.
 */
@OptIn(ExperimentalForeignApi::class)
private class AppleSmtpSocket(private val input: NSInputStream, private val output: NSOutputStream) : SmtpSocket {
    private val lines = LineBuffer()
    private val chunk = ByteArray(CHUNK_SIZE)
    private var pending = ByteArray(0)

    override var isSecure: Boolean = false
        private set

    fun enableTls() {
        input.setProperty(NSStreamSocketSecurityLevelNegotiatedSSL, NSStreamSocketSecurityLevelKey)
        output.setProperty(NSStreamSocketSecurityLevelNegotiatedSSL, NSStreamSocketSecurityLevelKey)
        isSecure = true
    }

    suspend fun awaitOpen() {
        while (input.streamStatus in OPENING || output.streamStatus in OPENING) delay(POLL)
        checkStatus()
    }

    override suspend fun readLine(): String? {
        while (true) {
            lines.pollLine()?.let { return it }
            if (!fill()) return null
        }
    }

    /** Read the available bytes into [lines]; false at the end of the stream. */
    private suspend fun fill(): Boolean {
        var wait = POLL
        while (!input.hasBytesAvailable) {
            if (input.streamStatus == NSStreamStatusAtEnd) return false
            checkStatus()
            delay(wait)
            wait = minOf(wait * 2, MAX_POLL)
        }
        val read = chunk.usePinned { input.read(it.addressOf(0).reinterpret(), CHUNK_SIZE.toULong()) }
        if (read > 0) lines.append(chunk, 0, read.toInt()) else checkStatus()
        return read != 0L
    }

    override suspend fun write(data: ByteArray) {
        pending += data
    }

    override suspend fun flush() {
        var offset = 0
        while (offset < pending.size) {
            var wait = POLL
            while (!output.hasSpaceAvailable) {
                checkStatus()
                delay(wait)
                wait = minOf(wait * 2, MAX_POLL)
            }
            val written = pending.usePinned {
                output.write(it.addressOf(offset).reinterpret(), (pending.size - offset).toULong())
            }
            if (written < 0) checkStatus()
            offset += written.toInt().coerceAtLeast(0)
        }
        pending = ByteArray(0)
    }

    override suspend fun startTls() {
        // data received before the handshake could have been injected by an attacker (CVE-2011-0411)
        if (!lines.isEmpty()) throw SmtpException("Server sent unexpected data before the TLS handshake")
        enableTls()
    }

    override fun close() {
        input.close()
        output.close()
    }

    private fun checkStatus() {
        for (stream in listOf(input, output)) {
            when (stream.streamStatus) {
                NSStreamStatusError -> {
                    throw SmtpException("Connection failed: ${stream.streamError?.localizedDescription}")
                }
                NSStreamStatusClosed -> throw SmtpException("Connection closed")
                else -> Unit
            }
        }
    }

    private companion object {
        const val CHUNK_SIZE = 8192
        val POLL = 1.milliseconds
        val MAX_POLL = 20.milliseconds
        val OPENING = setOf(NSStreamStatusNotOpen, NSStreamStatusOpening)
    }
}
