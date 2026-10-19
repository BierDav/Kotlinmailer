package at.quickme.kotlinmailer.smtp

import at.quickme.kotlinmailer.email.EmailAddress
import at.quickme.kotlinmailer.email.copy
import at.quickme.kotlinmailer.email.emailBuilder
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SmtpMailerTest {
    private val email = emailBuilder {
        from("Alice <alice@example.com>")
        to("bob@example.com")
        cc("carol@example.com")
        bcc("dave@example.com")
        subject = "Hello"
        text = "Hi!\n.hidden line\n.."
    }

    private fun mailer(server: FakeSmtpServer, block: SmtpMailerBuilder.() -> Unit = {}) =
        SmtpMailer(
            SmtpMailerBuilder("smtp.example.com").apply {
                socketFactory = server.factory
                credentials("user", "secret")
                block()
            }.build(),
        )

    @Test
    fun sendsEmailWithStartTlsAndAuth() = runTest {
        val server = FakeSmtpServer()
        val receipt = mailer(server).send(email)

        val commands = server.commands
        assertEquals("EHLO localhost", commands[0])
        assertEquals("STARTTLS", commands[1])
        assertEquals("EHLO localhost", commands[2])
        assertTrue(commands[3].startsWith("AUTH PLAIN "))
        assertTrue(commands[4].matches(Regex("MAIL FROM:<alice@example.com> SIZE=\\d+")), commands[4])
        assertEquals(
            listOf("RCPT TO:<bob@example.com>", "RCPT TO:<carol@example.com>", "RCPT TO:<dave@example.com>", "DATA"),
            commands.subList(5, 9),
        )

        assertEquals(email.messageId, receipt.messageId)
        assertEquals(3, receipt.acceptedRecipients.size)
        assertContains(receipt.serverResponse!!, "queued as 12345")

        val message = server.messages.single()
        assertContains(message, "Subject: Hello\r\n")
        assertFalse("dave@example.com" in message)
    }

    @Test
    fun dotStuffsMessageData() = runTest {
        val server = FakeSmtpServer()
        mailer(server).send(email)
        // the fake server stores the data as received, so leading dots are still doubled
        assertContains(server.messages.single(), "\r\n..hidden line\r\n...\r\n")
        assertEquals("a\r\n..b\r\n", dotStuff("a\r\n.b\r\n"))
        assertEquals("a\r\n", dotStuff("a"))
    }

    @Test
    fun pipelinesEnvelopeCommands() = runTest {
        val server = FakeSmtpServer()
        mailer(server).send(email)
        val envelope = server.batches.first { it.first().startsWith("MAIL") }
        assertEquals(4, envelope.size)
    }

    @Test
    fun sendsCommandsOneByOneWithoutPipelining() = runTest {
        val server = FakeSmtpServer(extensions = listOf("STARTTLS", "AUTH PLAIN"))
        mailer(server).send(email)
        assertTrue(server.batches.all { it.size == 1 })
        assertFalse(server.commands.any { "SIZE=" in it })
    }

    @Test
    fun failsAndResetsOnRejectedRecipient() = runTest {
        val server = FakeSmtpServer(rejectedRecipients = setOf("carol@example.com"))
        val error = assertFailsWith<SmtpException> { mailer(server).send(email) }
        assertEquals(550, error.replyCode)
        assertEquals("5.1.1", error.enhancedStatusCode)
        assertFalse(error.isTransient)
        assertEquals("RSET", server.commands.last())
        assertTrue(server.messages.isEmpty())
    }

    @Test
    fun deliversToAcceptedRecipientsIfConfigured() = runTest {
        val server = FakeSmtpServer(rejectedRecipients = setOf("carol@example.com"))
        val receipt = mailer(server) { failOnRejectedRecipient = false }.send(email)
        assertEquals(2, receipt.acceptedRecipients.size)
        assertEquals(setOf(EmailAddress("carol@example.com")), receipt.rejectedRecipients.keys)
        assertEquals(1, server.messages.size)
    }

    @Test
    fun failsIfAllRecipientsAreRejected() = runTest {
        val rejected = setOf("bob@example.com", "carol@example.com", "dave@example.com")
        val server = FakeSmtpServer(rejectedRecipients = rejected)
        val error = assertFailsWith<SmtpException> { mailer(server) { failOnRejectedRecipient = false }.send(email) }
        assertContains(error.message!!, "All recipients were rejected")
    }

    @Test
    fun requiresStartTlsWhenConfigured() = runTest {
        val server = FakeSmtpServer(extensions = listOf("AUTH PLAIN"))
        val error = assertFailsWith<SmtpException> { mailer(server).send(email) }
        assertContains(error.message!!, "STARTTLS")
        assertFalse(server.commands.any { it.startsWith("AUTH") })
    }

    @Test
    fun usesImplicitTlsWithoutStartTls() = runTest {
        val server = FakeSmtpServer()
        mailer(server) { security = SmtpSecurity.TLS }.send(email)
        assertFalse("STARTTLS" in server.commands)
    }

    @Test
    fun refusesCredentialsOverPlaintext() = runTest {
        val server = FakeSmtpServer()
        val error = assertFailsWith<SmtpException> { mailer(server) { security = SmtpSecurity.NONE }.send(email) }
        assertContains(error.message!!, "unencrypted")
        mailer(server) {
            security = SmtpSecurity.NONE
            allowInsecureAuth = true
        }.send(email)
    }

    @Test
    fun sendsWithoutAuthentication() = runTest {
        val server = FakeSmtpServer()
        mailer(server) {
            security = SmtpSecurity.NONE
            auth = null
        }.send(email)
        assertFalse(server.commands.any { it.startsWith("AUTH") })
    }

    @Test
    fun fallsBackToAuthLogin() = runTest {
        val server = FakeSmtpServer(extensions = listOf("STARTTLS", "AUTH LOGIN"))
        mailer(server).send(email)
        assertEquals("AUTH LOGIN", server.commands[3])
        assertEquals(Base64.encode("user".encodeToByteArray()), server.commands[4])
    }

    @Test
    fun reportsAuthenticationFailures() = runTest {
        val server = FakeSmtpServer()
        val error = assertFailsWith<SmtpException> { mailer(server) { credentials("user", "wrong") }.send(email) }
        assertEquals(535, error.replyCode)
    }

    @Test
    fun authenticatesWithOAuth2() = runTest {
        for (mechanism in listOf("XOAUTH2", "OAUTHBEARER")) {
            val server = FakeSmtpServer(extensions = listOf("STARTTLS", "AUTH $mechanism"))
            mailer(server) { oauth2("user") { "secret" } }.send(email)
            assertTrue(server.commands[3].startsWith("AUTH $mechanism "))

            val failing = FakeSmtpServer(extensions = listOf("STARTTLS", "AUTH $mechanism"))
            val error = assertFailsWith<SmtpException> { mailer(failing) { oauth2("user") { "expired" } }.send(email) }
            assertEquals(535, error.replyCode)
        }
    }

    @Test
    fun fallsBackToHelo() = runTest {
        val server = FakeSmtpServer(ehloSupported = false)
        mailer(server) {
            security = SmtpSecurity.STARTTLS_IF_AVAILABLE
            auth = null
        }.send(email)
        assertEquals(listOf("EHLO localhost", "HELO localhost"), server.commands.take(2))
        assertTrue(server.commands.any { it == "MAIL FROM:<alice@example.com>" })
    }

    @Test
    fun reusesConnections() = runTest {
        val server = FakeSmtpServer()
        val mailer = mailer(server)
        mailer.send(email)
        mailer.send(email.copy())
        assertEquals(1, server.connections)
        assertEquals(2, server.messages.size)

        mailer.close()
        assertEquals("QUIT", server.commands.last())
        assertFailsWith<IllegalStateException> { mailer.send(email) }
    }

    @Test
    fun opensNewConnectionsWithoutReuse() = runTest {
        val server = FakeSmtpServer()
        val mailer = mailer(server) { reuseConnections = false }
        mailer.send(email)
        mailer.send(email.copy())
        assertEquals(2, server.connections)
    }

    @Test
    fun dropsConnectionAfterServiceUnavailable() = runTest {
        val server = FakeSmtpServer(replies = mutableMapOf("MAIL" to "421 4.3.2 Shutting down"))
        val mailer = mailer(server)
        val error = assertFailsWith<SmtpException> { mailer.send(email) }
        assertTrue(error.isTransient)
        server.replies.clear()
        mailer.send(email)
        assertEquals(2, server.connections)
    }

    @Test
    fun rejectsTooLargeMessages() = runTest {
        val server = FakeSmtpServer(extensions = listOf("STARTTLS", "AUTH PLAIN", "SIZE 100"))
        val error = assertFailsWith<SmtpException> { mailer(server).send(email) }
        assertContains(error.message!!, "at most 100")
        assertFalse(server.commands.any { it.startsWith("MAIL") })
    }

    @Test
    fun usesSmtpUtf8ForInternationalizedAddresses() = runTest {
        val international = email.copy {
            to.clear()
            to("jörg@bücher.de")
        }
        assertFailsWith<SmtpException> { mailer(FakeSmtpServer()).send(international) }

        val server = FakeSmtpServer(extensions = listOf("STARTTLS", "AUTH PLAIN", "8BITMIME", "SMTPUTF8"))
        mailer(server).send(international)
        assertContains(server.commands, "MAIL FROM:<alice@example.com> BODY=8BITMIME SMTPUTF8")
        assertContains(server.commands, "RCPT TO:<jörg@bücher.de>")
    }

    @Test
    fun rejectsServiceRefusalInGreeting() = runTest {
        val server = FakeSmtpServer()
        val factory = SmtpSocketFactory { host, port, tls, timeout ->
            server.factory.connect(host, port, tls, timeout).also { it.readLine() }.let { socket ->
                object : SmtpSocket by socket {
                    var greeted = false

                    override suspend fun readLine(): String? =
                        if (!greeted) "554 No SMTP service here".also { greeted = true } else socket.readLine()
                }
            }
        }
        val error = assertFailsWith<SmtpException> { mailer(server) { socketFactory = factory }.send(email) }
        assertEquals(554, error.replyCode)
    }

    @Test
    fun timesOutWaitingForServer() = runTest {
        val server = FakeSmtpServer().apply { hang = true }
        val error = assertFailsWith<SmtpException> { mailer(server) { commandTimeout = 5.seconds }.send(email) }
        assertContains(error.message!!, "Timed out")
    }
}
