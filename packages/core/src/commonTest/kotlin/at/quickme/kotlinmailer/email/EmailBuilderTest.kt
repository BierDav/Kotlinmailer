package at.quickme.kotlinmailer.email

import at.quickme.kotlinmailer.mime.eml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EmailBuilderTest {
    private val original = emailBuilder {
        from("Alice <alice@example.com>")
        to("bob@example.com")
        cc("carol@example.com")
        replyTo("replies@example.com")
        subject = "Plans"
        text = "Lunch?\nTomorrow"
    }

    @Test
    fun requiresSenderAndRecipient() {
        assertFailsWith<IllegalStateException> { emailBuilder { to("bob@example.com") } }
        assertFailsWith<IllegalStateException> { emailBuilder { from("alice@example.com") } }
    }

    @Test
    fun rejectsHeaderInjection() {
        assertFailsWith<IllegalArgumentException> {
            emailBuilder {
                from("alice@example.com")
                to("bob@example.com")
                subject = "Hi\r\nBcc: evil@example.com"
            }
        }
        assertFailsWith<IllegalArgumentException> { Header("X-Test", "a\nb") }
        assertFailsWith<IllegalArgumentException> { Header("Content-Type", "text/plain") }
    }

    @Test
    fun generatesMessageIdFromSenderDomain() {
        assertTrue(original.messageId.endsWith("@example.com"))
        assertNotEquals(original.messageId, original.copy().messageId)
    }

    @Test
    fun appendsAndPrependsText() {
        val email = original.copy {
            appendText("!")
            prependText("> ")
        }
        assertEquals("> Lunch?\nTomorrow!", email.text)
    }

    @Test
    fun repliesToReplyToAddressAndKeepsThread() {
        val reply = original.reply(EmailAddress("bob@example.com")) { prependText("Sure!") }
        assertEquals(listOf(EmailAddress("replies@example.com")), reply.to)
        assertEquals("Re: Plans", reply.subject)
        assertEquals(listOf(original.messageId), reply.inReplyTo)
        assertEquals(listOf(original.messageId), reply.references)
        assertTrue(reply.text!!.startsWith("Sure!"))
        assertTrue("> Lunch?\r\n> Tomorrow" in reply.text)
        assertEquals("Re: Plans", reply.reply(EmailAddress("alice@example.com")).subject)
    }

    @Test
    fun replyToAllExcludesSender() {
        val reply = original.reply(EmailAddress("bob@example.com"), toAll = true)
        assertEquals(listOf(EmailAddress("carol@example.com")), reply.cc)
    }

    @Test
    fun forwardsAsMessageAttachment() {
        val forward = original.forward(EmailAddress("bob@example.com")) { to("dave@example.com") }
        assertEquals("Fwd: Plans", forward.subject)
        val attachment = forward.attachments.single()
        assertEquals("message/rfc822", attachment.contentType)
        assertTrue("Subject: Plans" in attachment.content.decodeToString())
        assertTrue("Content-Transfer-Encoding: 7bit" in forward.eml)
    }

    @Test
    fun guessesContentTypes() {
        assertEquals("application/pdf", Attachment.guessContentType("Invoice.PDF"))
        assertEquals("application/octet-stream", Attachment.guessContentType("file"))
    }
}
