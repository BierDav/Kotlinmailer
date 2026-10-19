package at.quickme.kotlinmailer.delivery

import at.quickme.kotlinmailer.email.Email
import at.quickme.kotlinmailer.email.emailBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MailerManagerTest {
    private class RecordingMailer : Mailer {
        val sent = mutableListOf<Email>()
        var closed = false

        override suspend fun send(email: Email): SendReceipt {
            sent += email
            return SendReceipt(email.messageId, email.recipients)
        }

        override suspend fun close() {
            closed = true
        }
    }

    @Test
    fun sendsWithDefaultMailerAndShutsDown() = runTest {
        MailerManager.shutdownMailers()
        assertFailsWith<IllegalStateException> { MailerManager.defaultMailer }

        val mailer = RecordingMailer()
        val other = RecordingMailer()
        MailerManager.defaultMailer = mailer
        MailerManager.registerMailer(other)
        assertSame(mailer, MailerManager.defaultMailer)

        val email = emailBuilder {
            from("alice@example.com")
            to("bob@example.com")
        }
        val receipt = email.send()
        assertEquals(email.messageId, receipt.messageId)
        assertEquals(listOf(email), mailer.sent)

        MailerManager.shutdownMailers()
        assertTrue(mailer.closed && other.closed)
        assertFalse(MailerManager.hasDefaultMailer)
    }
}
