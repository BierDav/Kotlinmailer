package at.quickme.kotlinmailer.smtp

import at.quickme.kotlinmailer.email.emailBuilder
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.InternetAddress
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GreenMailIntegrationTest {
    private val greenMail = GreenMail(arrayOf(ServerSetupTest.SMTP.dynamicPort(), ServerSetupTest.SMTPS.dynamicPort()))
        .withConfiguration(GreenMailConfiguration.aConfig().withUser("user", "secret"))

    // GreenMail uses a self-signed certificate
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val attachment = ByteArray(10_000) { (it % 251).toByte() }

    private val email = emailBuilder {
        from("Jörg Müller <sender@example.com>")
        to("alice@example.com", "Alice")
        cc("bob@example.com")
        bcc("carol@example.com")
        subject = "Grüße, a long subject line that definitely has to be folded because it is longer than 78 chars ✓"
        text = "Hallo Alice,\n.this line starts with a dot\nGrüße"
        html = "<p>Hallo <b>Alice</b> <img src=\"cid:logo@example.com\"></p>"
        embed("logo@example.com", byteArrayOf(1, 2, 3), "image/png")
        attach("Übersicht.bin", attachment)
        header("X-Custom", "value")
    }

    @BeforeTest
    fun start() = greenMail.start()

    @AfterTest
    fun stop() = greenMail.stop()

    private fun mailer(security: SmtpSecurity, port: Int) = smtpMailer("localhost") {
        this.security = security
        this.port = port
        credentials("user", "secret")
        allowInsecureAuth = security == SmtpSecurity.NONE
        socketFactory = KtorSmtpSocketFactory { trustManager = trustAll }
    }

    @Test
    fun sendsOverPlainSmtp() = runBlocking {
        val mailer = mailer(SmtpSecurity.NONE, greenMail.smtp.port)
        val receipt = mailer.send(email)
        mailer.close()
        assertEquals(3, receipt.acceptedRecipients.size)
        verifyReceived()
    }

    @Test
    fun sendsOverImplicitTls() = runBlocking {
        val mailer = mailer(SmtpSecurity.TLS, greenMail.smtps.port)
        mailer.send(email)
        mailer.close()
        verifyReceived()
    }

    @Test
    fun sendsInParallelOverMultipleConnections() = runBlocking {
        val mailer = smtpMailer("localhost") {
            security = SmtpSecurity.NONE
            port = greenMail.smtp.port
            maxConnections = 4
        }
        (1..10).map { async { mailer.send(email) } }.awaitAll()
        mailer.close()
        assertEquals(30, greenMail.receivedMessages.size)
    }

    @Test
    fun failsWithWrongPassword() = runBlocking {
        val mailer = smtpMailer("localhost") {
            security = SmtpSecurity.NONE
            port = greenMail.smtp.port
            allowInsecureAuth = true
            credentials("user", "wrong")
        }
        val error = assertFailsWith<SmtpException> { mailer.send(email) }
        assertEquals(535, error.replyCode)
        mailer.close()
    }

    @Test
    fun failsToConnectToClosedPort() = runBlocking {
        val mailer = smtpMailer("localhost") {
            security = SmtpSecurity.NONE
            port = 1
        }
        assertFailsWith<SmtpException> { mailer.testConnection() }
        Unit
    }

    private fun verifyReceived() {
        assertTrue(greenMail.waitForIncomingEmail(5_000, 3))
        val messages = greenMail.receivedMessages
        assertEquals(3, messages.size)
        val message = messages.first()

        assertEquals(email.subject, message.subject)
        val from = message.from.single() as InternetAddress
        assertEquals("Jörg Müller", from.personal)
        assertEquals("sender@example.com", from.address)
        assertEquals("Alice", (message.getRecipients(Message.RecipientType.TO).single() as InternetAddress).personal)
        assertEquals(null, message.getRecipients(Message.RecipientType.BCC))
        assertEquals("<${email.messageId}>", message.messageID)
        assertEquals("value", message.getHeader("X-Custom").single())

        val mixed = message.content as Multipart
        assertTrue(message.isMimeType("multipart/mixed"))
        val alternative = mixed.getBodyPart(0).content as Multipart
        assertEquals("Hallo Alice,\r\n.this line starts with a dot\r\nGrüße", alternative.getBodyPart(0).content)
        val related = alternative.getBodyPart(1).content as Multipart
        assertTrue(related.getBodyPart(0).content.toString().startsWith("<p>Hallo"))
        assertEquals("<logo@example.com>", related.getBodyPart(1).getHeader("Content-ID").single())

        val file = mixed.getBodyPart(1)
        assertEquals(Part.ATTACHMENT, file.disposition)
        assertEquals("Übersicht.bin", file.fileName)
        assertContentEquals(attachment, file.inputStream.readBytes())
    }
}
