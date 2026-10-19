package at.quickme.kotlinmailer.smtp

import kotlinx.coroutines.awaitCancellation
import kotlin.io.encoding.Base64

/** A scriptable in-memory SMTP server speaking enough RFC 5321 to test the client. */
class FakeSmtpServer(
    var extensions: List<String> = listOf("PIPELINING", "SIZE 10000000", "8BITMIME", "STARTTLS", "AUTH PLAIN LOGIN"),
    val rejectedRecipients: Set<String> = emptySet(),
    var ehloSupported: Boolean = true,
    val credentials: Pair<String, String> = "user" to "secret",
    val replies: MutableMap<String, String> = mutableMapOf(),
) {
    val commands = mutableListOf<String>()
    val messages = mutableListOf<String>()

    /** The commands sent in each flush, to check pipelining. */
    val batches = mutableListOf<List<String>>()
    var connections = 0
    var hang = false

    val factory = SmtpSocketFactory { _, _, implicitTls, _ ->
        connections++
        Socket(implicitTls)
    }

    inner class Socket(implicitTls: Boolean) : SmtpSocket {
        override var isSecure = implicitTls
        private val output = ArrayDeque(listOf("220 fake.example.com ESMTP ready"))
        private val input = StringBuilder()
        private var data: StringBuilder? = null
        private var authState: String? = null
        var closed = false

        override suspend fun readLine(): String? {
            if (hang) awaitCancellation()
            return output.removeFirstOrNull()
        }

        override suspend fun write(data: ByteArray) {
            input.append(data.decodeToString())
        }

        override suspend fun flush() {
            val batch = mutableListOf<String>()
            while (true) {
                val end = input.indexOf("\r\n").takeIf { it >= 0 } ?: break
                val line = input.substring(0, end)
                input.deleteRange(0, end + 2)
                if (data == null) batch += line
                handle(line)
            }
            if (batch.isNotEmpty()) batches += batch
        }

        override suspend fun startTls() {
            isSecure = true
        }

        override fun close() {
            closed = true
        }

        private fun decode(base64: String) = Base64.decode(base64).decodeToString()

        private fun reply(value: String) {
            output.addAll(value.split("\n"))
        }

        private fun handle(line: String) {
            data?.let { body ->
                if (line == ".") {
                    messages += body.toString()
                    data = null
                    reply("250 2.0.0 Ok: queued as 12345")
                } else {
                    body.append(line).append("\r\n")
                }
                return
            }
            authState?.let { state ->
                commands += line
                authState = null
                when (state) {
                    "LOGIN_USER" -> {
                        authState = if (decode(line) == credentials.first) "LOGIN_PASS" else null
                        reply("334 UGFzc3dvcmQ6")
                    }

                    "LOGIN_PASS" -> reply(if (decode(line) == credentials.second) "235 ok" else "535 bad")
                    else -> reply("535 5.7.8 Authentication failed")
                }
                return
            }

            commands += line
            val verb = line.substringBefore(' ').uppercase()
            replies[verb]?.let { return reply(it) }
            when (verb) {
                "EHLO" -> if (!ehloSupported) reply("502 Command not implemented") else {
                    val ext = extensions.filter { !(isSecure && it == "STARTTLS") }
                    reply((listOf("fake.example.com Hello") + ext).let { lines ->
                        lines.mapIndexed { i, l -> if (i == lines.lastIndex) "250 $l" else "250-$l" }.joinToString("\n")
                    })
                }

                "HELO" -> reply("250 fake.example.com")
                "STARTTLS" -> reply("220 2.0.0 Ready to start TLS")
                "AUTH" -> auth(line.split(' ').drop(1))
                "MAIL" -> reply("250 2.1.0 Ok")
                "RCPT" -> {
                    val address = line.substringAfter('<').substringBefore('>')
                    reply(if (address in rejectedRecipients) "550 5.1.1 User unknown" else "250 2.1.5 Ok")
                }

                "DATA" -> {
                    data = StringBuilder()
                    reply("354 End data with <CR><LF>.<CR><LF>")
                }

                "RSET", "NOOP" -> reply("250 2.0.0 Ok")
                "QUIT" -> reply("221 2.0.0 Bye")
                else -> reply("500 5.5.2 Unknown command")
            }
        }

        private fun auth(args: List<String>) {
            val (user, password) = credentials
            when (args.firstOrNull()?.uppercase()) {
                "PLAIN" -> {
                    val decoded = Base64.decode(args[1]).decodeToString()
                    reply(if (decoded == "\u0000$user\u0000$password") "235 2.7.0 Authenticated" else "535 5.7.8 Bad")
                }

                "LOGIN" -> {
                    authState = "LOGIN_USER"
                    reply("334 VXNlcm5hbWU6")
                }

                "XOAUTH2", "OAUTHBEARER" -> {
                    val decoded = Base64.decode(args[1]).decodeToString()
                    if ("auth=Bearer $password\u0001" in decoded && user in decoded) {
                        reply("235 2.7.0 Authenticated")
                    } else {
                        authState = "OAUTH_ERROR"
                        reply("334 eyJzdGF0dXMiOiI0MDEifQ==")
                    }
                }

                else -> reply("504 5.5.4 Unrecognized authentication type")
            }
        }
    }
}
