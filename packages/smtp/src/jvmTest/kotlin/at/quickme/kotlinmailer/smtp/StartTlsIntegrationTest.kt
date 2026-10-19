package at.quickme.kotlinmailer.smtp

import at.quickme.kotlinmailer.email.emailBuilder
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Uses a minimal JDK based server, because GreenMail does not implement STARTTLS. */
class StartTlsIntegrationTest {
    private val server = ServerSocket(0)
    private val log: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val sslContext = SSLContext.getInstance("TLS").apply {
        val keyStore = KeyStore.getInstance("PKCS12")
        val resource = StartTlsIntegrationTest::class.java.classLoader.getResourceAsStream("greenmail.p12")
        keyStore.load(resource, "changeit".toCharArray())
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keys.init(keyStore, "changeit".toCharArray())
        init(keys.keyManagers, null, null)
    }

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val email = emailBuilder {
        from("alice@example.com")
        to("bob@example.com")
        subject = "Over STARTTLS"
        text = "secret"
    }

    @AfterTest
    fun stop() = server.close()

    private fun serve(injectAfterStartTls: Boolean = false) = thread(isDaemon = true) {
        var socket: Socket = server.accept()
        var reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        var out: OutputStream = socket.getOutputStream()
        fun send(line: String) = out.write("$line\r\n".toByteArray()).also { out.flush() }
        val secure = { socket is SSLSocket }

        send("220 starttls.test ESMTP")
        while (true) {
            val line = reader.readLine() ?: break
            log += (if (secure()) "tls: " else "plain: ") + line
            when (line.substringBefore(' ')) {
                "EHLO" -> if (secure()) {
                    send("250-starttls.test\r\n250 AUTH PLAIN")
                } else {
                    send("250-starttls.test\r\n250-STARTTLS\r\n250 AUTH PLAIN")
                }

                "STARTTLS" -> {
                    send(if (injectAfterStartTls) "220 Go ahead\r\n250 injected" else "220 Go ahead")
                    if (injectAfterStartTls) break
                    socket = (sslContext.socketFactory.createSocket(socket, null, socket.port, true) as SSLSocket)
                        .apply { useClientMode = false }
                    reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    out = socket.getOutputStream()
                }

                "AUTH" -> send("235 2.7.0 Authenticated")
                "MAIL", "RCPT" -> send("250 Ok")
                "DATA" -> {
                    send("354 Go ahead")
                    do {
                        val data = reader.readLine()
                        log += "data: $data"
                    } while (data != ".")
                    send("250 Ok queued")
                }

                "QUIT" -> {
                    send("221 Bye")
                    break
                }

                else -> send("500 Unknown")
            }
        }
        socket.close()
    }

    private fun mailer() = smtpMailer("localhost") {
        port = server.localPort
        security = SmtpSecurity.STARTTLS
        credentials("user", "secret")
        socketFactory = KtorSmtpSocketFactory { trustManager = trustAll }
    }

    @Test
    fun upgradesToTlsBeforeAuthentication() = runBlocking {
        val thread = serve()
        val mailer = mailer()
        mailer.send(email)
        mailer.close()
        thread.join(5_000)

        assertEquals(listOf("plain: EHLO localhost", "plain: STARTTLS", "tls: EHLO localhost"), log.take(3))
        assertTrue(log.filter { it.startsWith("plain") }.none { "AUTH" in it || "MAIL" in it })
        assertContains(log, "data: secret")
    }

    @Test
    fun rejectsUntrustedCertificates() = runBlocking {
        serve()
        val mailer = smtpMailer("localhost") {
            port = server.localPort
            credentials("user", "secret")
        }
        assertFailsWith<SmtpException> { mailer.send(email) }
        assertTrue(log.none { "AUTH" in it })
    }

    @Test
    fun rejectsDataInjectedBeforeTlsHandshake() = runBlocking {
        serve(injectAfterStartTls = true)
        val error = assertFailsWith<SmtpException> { mailer().send(email) }
        assertContains(error.message!!, "unexpected data")
    }
}
