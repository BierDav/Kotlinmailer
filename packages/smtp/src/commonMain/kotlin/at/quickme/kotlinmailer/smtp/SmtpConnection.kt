package at.quickme.kotlinmailer.smtp

import at.quickme.kotlinmailer.delivery.SendReceipt
import at.quickme.kotlinmailer.email.Email
import at.quickme.kotlinmailer.email.EmailAddress
import at.quickme.kotlinmailer.mime.RenderedEmail
import at.quickme.kotlinmailer.mime.render
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.io.encoding.Base64
import kotlin.time.Duration

/** One SMTP session (RFC 5321) on top of an [SmtpSocket]. */
internal class SmtpConnection(private val socket: SmtpSocket, private val config: SmtpConfig) {
    var extensions: SmtpExtensions = SmtpExtensions.NONE
        private set

    /** False once the session is in an unknown state and must not be reused. */
    var isUsable: Boolean = true
        private set

    suspend fun open() = guard {
        expect("greeting", read(config.commandTimeout), ReplyCode.SERVICE_READY)
        ehlo()
        val canStartTls = !socket.isSecure && "STARTTLS" in extensions
        when (config.security) {
            SmtpSecurity.STARTTLS -> {
                if (!socket.isSecure && !canStartTls) {
                    throw SmtpException("Server ${config.host} does not support STARTTLS")
                }
                if (canStartTls) startTls()
            }

            SmtpSecurity.STARTTLS_IF_AVAILABLE -> if (canStartTls) startTls()
            SmtpSecurity.TLS, SmtpSecurity.NONE -> Unit
        }
        config.auth?.let { authenticate(it) }
    }

    suspend fun send(email: Email): SendReceipt = guard {
        val rendered = email.render()
        val data = dotStuff(rendered.content).encodeToByteArray()
        checkSupported(rendered, data.size)

        val mailParams = buildList {
            if ("SIZE" in extensions) add("SIZE=${data.size}")
            if (rendered.requires8BitMime) add("BODY=8BITMIME")
            if (rendered.requiresSmtpUtf8) add("SMTPUTF8")
        }
        val recipients = email.recipients
        val commands = listOf("MAIL FROM:<${email.from.address}>" + mailParams.joinToString("") { " $it" }) +
            recipients.map { "RCPT TO:<${it.address}>" }

        val replies = if ("PIPELINING" in extensions) {
            commands.forEach { write(it) }
            socket.flush()
            commands.map { read(config.commandTimeout) }
        } else {
            commands.map { command(it) }
        }

        val mailReply = replies.first()
        if (mailReply.code != ReplyCode.OK) failTransaction(SmtpException.from("MAIL FROM", mailReply))
        val (accepted, rejected) = checkRecipients(recipients, replies.drop(1))

        val dataReply = command("DATA")
        if (dataReply.code != ReplyCode.START_MAIL_INPUT) failTransaction(SmtpException.from("DATA", dataReply))
        socket.write(data)
        socket.write(".\r\n".encodeToByteArray())
        socket.flush()
        val result = read(config.dataTimeout)
        if (result.code != ReplyCode.OK) throw SmtpException.from("message transfer", result)

        SendReceipt(email.messageId, accepted, rejected, result.message)
    }

    private fun checkSupported(rendered: RenderedEmail, size: Int) {
        if (rendered.requiresSmtpUtf8 && "SMTPUTF8" !in extensions) {
            throw SmtpException("Email contains internationalized addresses, but the server does not support SMTPUTF8")
        }
        if (rendered.requires8BitMime && "8BITMIME" !in extensions) {
            throw SmtpException("Email contains 8-bit data, but the server does not support 8BITMIME")
        }
        val max = extensions.maxSize
        if (max != null && size > max) throw SmtpException("Email has $size bytes, but the server accepts at most $max")
    }

    /** Split recipients into accepted and rejected ones, failing the transaction if needed. */
    private suspend fun checkRecipients(
        recipients: List<EmailAddress>,
        replies: List<SmtpReply>,
    ): Pair<List<EmailAddress>, Map<EmailAddress, String>> {
        val accepted = mutableListOf<EmailAddress>()
        val rejected = linkedMapOf<EmailAddress, String>()
        var firstRejection: SmtpReply? = null
        for ((recipient, reply) in recipients.zip(replies)) {
            if (reply.code == ReplyCode.OK || reply.code == ReplyCode.WILL_FORWARD) {
                accepted += recipient
            } else {
                rejected[recipient] = reply.toString()
                firstRejection = firstRejection ?: reply
            }
        }
        firstRejection?.let { reply ->
            val message = when {
                accepted.isEmpty() -> "All recipients were rejected: $rejected"
                config.failOnRejectedRecipient -> "Recipients were rejected: $rejected"
                else -> null
            }
            if (message != null) failTransaction(SmtpException(message, reply.code, reply.enhancedStatusCode))
        }
        return accepted to rejected
    }

    suspend fun reset() = guard { expect("RSET", command("RSET"), ReplyCode.OK) }

    suspend fun noop(): Boolean = runCatching { guard { command("NOOP").code == ReplyCode.OK } }.getOrDefault(false)

    suspend fun quit() {
        try {
            if (isUsable) command("QUIT")
        } catch (_: Exception) {
            // the connection is closed anyway
        } finally {
            isUsable = false
            socket.close()
        }
    }

    private suspend fun ehlo() {
        val reply = command("EHLO ${config.clientHostname}")
        extensions = if (reply.code == ReplyCode.OK) {
            SmtpExtensions.parse(reply)
        } else {
            // RFC 5321 4.1.4: fall back to HELO for servers without ESMTP
            expect("HELO", command("HELO ${config.clientHostname}"), ReplyCode.OK)
            SmtpExtensions.NONE
        }
    }

    private suspend fun startTls() {
        expect("STARTTLS", command("STARTTLS"), ReplyCode.SERVICE_READY)
        socket.startTls()
        // RFC 3207 4.2: discard all knowledge obtained before TLS
        ehlo()
    }

    private suspend fun authenticate(auth: SmtpAuth) {
        if (!socket.isSecure && !config.allowInsecureAuth) {
            throw SmtpException("Refusing to send credentials over an unencrypted connection")
        }
        val mechanisms = extensions.authMechanisms
        if (mechanisms.isEmpty()) throw SmtpException("Server ${config.host} does not support authentication")

        when (auth) {
            is SmtpAuth.Password -> authPassword(auth, mechanisms)
            is SmtpAuth.OAuth2 -> authOAuth2(auth, mechanisms)
        }
    }

    private suspend fun authPassword(auth: SmtpAuth.Password, mechanisms: Set<String>) = when {
        "PLAIN" in mechanisms -> authPlain(auth)
        "LOGIN" in mechanisms -> authLogin(auth)
        else -> throw SmtpException("No supported password authentication mechanism in $mechanisms")
    }

    private suspend fun authOAuth2(auth: SmtpAuth.OAuth2, mechanisms: Set<String>) = when {
        "OAUTHBEARER" in mechanisms -> authOAuthBearer(auth.username, auth.accessToken())
        "XOAUTH2" in mechanisms -> authXOAuth2(auth.username, auth.accessToken())
        else -> throw SmtpException("No supported OAuth 2.0 authentication mechanism in $mechanisms")
    }

    private suspend fun authPlain(auth: SmtpAuth.Password) {
        val response = base64("\u0000${auth.username}\u0000${auth.password}")
        var reply = command("AUTH PLAIN $response")
        if (reply.code == ReplyCode.AUTH_CHALLENGE) reply = command(response)
        expect("AUTH PLAIN", reply, ReplyCode.AUTH_SUCCEEDED)
    }

    private suspend fun authLogin(auth: SmtpAuth.Password) {
        expect("AUTH LOGIN", command("AUTH LOGIN"), ReplyCode.AUTH_CHALLENGE)
        expect("AUTH LOGIN", command(base64(auth.username)), ReplyCode.AUTH_CHALLENGE)
        expect("AUTH LOGIN", command(base64(auth.password)), ReplyCode.AUTH_SUCCEEDED)
    }

    private suspend fun authXOAuth2(username: String, token: String) {
        var reply = command("AUTH XOAUTH2 " + base64("user=$username\u0001auth=Bearer $token\u0001\u0001"))
        // on failure the server sends a 334 error challenge that has to be answered with an empty line
        if (reply.code == ReplyCode.AUTH_CHALLENGE) reply = command("")
        expect("AUTH XOAUTH2", reply, ReplyCode.AUTH_SUCCEEDED)
    }

    private suspend fun authOAuthBearer(username: String, token: String) {
        val user = username.replace("=", "=3D").replace(",", "=2C")
        val payload = "n,a=$user,\u0001host=${config.host}\u0001port=${config.port}\u0001auth=Bearer $token\u0001\u0001"
        var reply = command("AUTH OAUTHBEARER " + base64(payload))
        // RFC 7628 3.2.3: answer the error challenge with a dummy response
        if (reply.code == ReplyCode.AUTH_CHALLENGE) reply = command(base64("\u0001"))
        expect("AUTH OAUTHBEARER", reply, ReplyCode.AUTH_SUCCEEDED)
    }

    /** Reset the transaction after a rejected command, keeping the session usable. */
    private suspend fun failTransaction(error: SmtpException): Nothing {
        reset()
        throw error
    }

    private suspend fun command(line: String): SmtpReply {
        write(line)
        socket.flush()
        return read(config.commandTimeout)
    }

    private suspend fun write(line: String) = socket.write("$line\r\n".encodeToByteArray())

    private suspend fun read(timeout: Duration): SmtpReply = try {
        withTimeout(timeout) { SmtpReply.read(socket) }
    } catch (e: TimeoutCancellationException) {
        throw SmtpException("Timed out after $timeout waiting for the server", cause = e)
    }

    private fun expect(command: String, reply: SmtpReply, code: Int) {
        if (reply.code != code) throw SmtpException.from(command, reply)
    }

    /** Marks the session unusable if anything other than a cleanly handled rejection happens. */
    private suspend inline fun <T> guard(block: () -> T): T {
        var clean = false
        try {
            return block().also { clean = true }
        } catch (e: SmtpException) {
            // a rejected command leaves the session usable, everything else does not
            clean = e.replyCode != null && e.replyCode != ReplyCode.SERVICE_UNAVAILABLE
            throw e
        } finally {
            if (!clean) isUsable = false
        }
    }

    private companion object {
        fun base64(value: String) = Base64.encode(value.encodeToByteArray())
    }
}

/** RFC 5321 4.5.2 transparency: double leading dots and make sure the data ends with CRLF. */
internal fun dotStuff(content: String): String {
    val lines = content.split("\r\n")
    val body = (if (content.endsWith("\r\n")) lines.dropLast(1) else lines)
    return buildString(content.length + body.size) {
        for (line in body) {
            if (line.startsWith('.')) append('.')
            append(line).append("\r\n")
        }
    }
}
