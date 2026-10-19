package at.quickme.kotlinmailer.smtp

import at.quickme.kotlinmailer.delivery.Mailer
import at.quickme.kotlinmailer.delivery.SendReceipt
import at.quickme.kotlinmailer.email.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Sends emails via SMTP, keeping up to [SmtpConfig.maxConnections] connections open.
 *
 * Create one with [smtpMailer] and [close] it when done.
 */
public class SmtpMailer(public val config: SmtpConfig) : Mailer {
    private class Idle(val connection: SmtpConnection, val since: TimeSource.Monotonic.ValueTimeMark)

    private val permits = Semaphore(config.maxConnections)
    private val lock = Mutex()
    private val idle = ArrayDeque<Idle>()
    private var closed = false

    override suspend fun send(email: Email): SendReceipt = permits.withPermit {
        val connection = acquire()
        try {
            translateErrors("Failed to send email") { connection.send(email) }.also { release(connection) }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { connection.quit() }
            throw e
        } catch (e: SmtpException) {
            // connections that failed with anything but a rejection are no longer usable and get closed
            release(connection)
            throw e
        }
    }

    /** Open a connection and authenticate, to check the configuration. */
    public suspend fun testConnection() {
        permits.withPermit { release(acquire()) }
    }

    override suspend fun close() {
        val connections = lock.withLock {
            closed = true
            idle.map { it.connection }.also { idle.clear() }
        }
        connections.forEach { it.quit() }
    }

    private suspend fun acquire(): SmtpConnection {
        while (true) {
            val candidate = lock.withLock {
                check(!closed) { "SmtpMailer has been closed" }
                idle.removeLastOrNull()
            } ?: break
            // servers drop idle sessions, so check older ones before reuse
            if (candidate.since.elapsedNow() < IDLE_CHECK_AFTER || candidate.connection.noop()) {
                return candidate.connection
            }
            candidate.connection.quit()
        }
        return connect()
    }

    private suspend fun release(connection: SmtpConnection) {
        val keep = connection.isUsable && config.reuseConnections && lock.withLock {
            !closed && idle.add(Idle(connection, TimeSource.Monotonic.markNow()))
        }
        if (!keep) connection.quit()
    }

    @Suppress("TooGenericExceptionCaught") // close the connection on any failure
    private suspend fun connect(): SmtpConnection {
        val socket = translateErrors("Could not connect to ${config.host}:${config.port}") {
            config.socketFactory.connect(
                config.host,
                config.port,
                implicitTls = config.security == SmtpSecurity.TLS,
                timeout = config.connectTimeout,
            )
        }
        val connection = SmtpConnection(socket, config)
        try {
            translateErrors("Could not open SMTP session with ${config.host}") { connection.open() }
        } catch (e: Throwable) {
            withContext(NonCancellable) { connection.quit() }
            throw e
        }
        return connection
    }

    /** Platform socket errors have no common type, so they are wrapped into [SmtpException]. */
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> translateErrors(context: String, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: SmtpException) {
        throw e
    } catch (e: Exception) {
        throw SmtpException("$context: ${e.message}", cause = e)
    }

    private companion object {
        val IDLE_CHECK_AFTER = 30.seconds
    }
}
