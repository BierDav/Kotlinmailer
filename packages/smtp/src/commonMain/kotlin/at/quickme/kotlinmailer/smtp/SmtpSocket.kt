package at.quickme.kotlinmailer.smtp

import kotlin.time.Duration

/**
 * A bidirectional byte stream to an SMTP server that can be upgraded to TLS.
 *
 * Implement this together with [SmtpSocketFactory] to run SMTP over a custom transport.
 */
public interface SmtpSocket {
    /** True once the connection is encrypted. */
    public val isSecure: Boolean

    /** Read a line without its line terminator; `null` once the connection is closed. */
    public suspend fun readLine(): String?

    /** Queue [data] for sending; call [flush] to actually send it. */
    public suspend fun write(data: ByteArray)

    public suspend fun flush()

    /** Upgrade the connection to TLS in place (STARTTLS), verifying the server's certificate. */
    public suspend fun startTls()

    public fun close()
}

/** Opens [SmtpSocket]s. */
public fun interface SmtpSocketFactory {
    /**
     * Connect to [host] on [port].
     *
     * @param implicitTls perform the TLS handshake immediately after connecting (SMTPS, RFC 8314)
     */
    public suspend fun connect(host: String, port: Int, implicitTls: Boolean, timeout: Duration): SmtpSocket
}

/** The socket factory of the current platform. */
public expect fun defaultSmtpSocketFactory(): SmtpSocketFactory

/** Splits received bytes into lines terminated by CRLF (or a bare LF). */
internal class LineBuffer(private val maxLineLength: Int = MAX_REPLY_LINE) {
    private var buffer = ByteArray(INITIAL_SIZE)
    private var size = 0

    fun append(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        if (size + length > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + length))
        data.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    fun pollLine(): String? {
        val lf = (0 until size).firstOrNull { buffer[it] == '\n'.code.toByte() }
        if (lf == null) {
            if (size > maxLineLength) throw SmtpException("Server sent a line longer than $maxLineLength bytes")
            return null
        }
        val end = if (lf > 0 && buffer[lf - 1] == '\r'.code.toByte()) lf - 1 else lf
        val line = buffer.decodeToString(0, end)
        buffer.copyInto(buffer, 0, lf + 1, size)
        size -= lf + 1
        return line
    }

    /** True if no unread bytes are buffered, which must hold before a TLS handshake. */
    fun isEmpty() = size == 0

    private companion object {
        const val INITIAL_SIZE = 1024
        const val MAX_REPLY_LINE = 64 * 1024
    }
}
