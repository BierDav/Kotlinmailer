package at.quickme.kotlinmailer.mime

import at.quickme.kotlinmailer.email.EmailAddress
import at.quickme.kotlinmailer.email.copy
import at.quickme.kotlinmailer.email.emailBuilder
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class MimeRendererTest {
    private val date = Instant.parse("2024-03-05T07:08:09Z")

    private fun simple() = emailBuilder {
        from("John Doe <john@example.com>")
        to("alice@example.com", "Alice")
        cc("bob@example.com")
        bcc("secret@example.com")
        subject = "Hello"
        text = "Hi Alice,\nhow are you?"
        messageId = "abc@example.com"
        this.date = this@MimeRendererTest.date
    }

    private fun String.headers() = substringBefore("\r\n\r\n").replace("\r\n ", " ").split("\r\n")
        .associate { it.substringBefore(": ") to it.substringAfter(": ") }

    @Test
    fun rendersPlainTextEmail() {
        val rendered = simple().render()
        val headers = rendered.content.headers()
        assertEquals("John Doe <john@example.com>", headers["From"])
        assertEquals("Alice <alice@example.com>", headers["To"])
        assertEquals("bob@example.com", headers["Cc"])
        assertEquals("Hello", headers["Subject"])
        assertEquals("<abc@example.com>", headers["Message-ID"])
        assertEquals("1.0", headers["MIME-Version"])
        assertEquals("text/plain; charset=UTF-8", headers["Content-Type"])
        assertEquals("7bit", headers["Content-Transfer-Encoding"])
        assertTrue(headers["Date"]!!.matches(Regex("""\w{3}, \d{1,2} \w{3} \d{4} \d{2}:\d{2}:\d{2} [+-]\d{4}""")))
        assertTrue(rendered.content.endsWith("\r\n\r\nHi Alice,\r\nhow are you?\r\n"))
        assertFalse(rendered.requiresSmtpUtf8)
        assertFalse(rendered.requires8BitMime)
    }

    @Test
    fun neverRendersBcc() {
        val content = simple().eml
        assertFalse("secret@example.com" in content)
        assertFalse("Bcc" in content)
    }

    @Test
    fun usesOnlyCrlfAndShortLines() {
        val email = simple().copy {
            subject = "A very long subject ".repeat(20) + "with ünïcödé"
            text = "Line\nwith\rmixed\r\nendings " + "long ".repeat(300)
            html = "<p>${"ü".repeat(500)}</p>"
            attach("data.bin", ByteArray(5000) { it.toByte() })
        }
        val content = email.render().content
        assertFalse(Regex("[^\r]\n|\r[^\n]").containsMatchIn(content), "bare CR or LF found")
        content.split("\r\n").forEach { assertTrue(it.length <= 998, "line too long: ${it.length}") }
    }

    @Test
    fun encodesNonAsciiHeaders() {
        val content = simple().copy {
            from = EmailAddress("jorg@example.com", "Jörg Müller")
            subject = "Grüße aus Österreich"
        }.eml
        val headers = content.headers()
        assertTrue(headers["Subject"]!!.startsWith("=?UTF-8?B?"))
        assertTrue(headers["From"]!!.matches(Regex("""=\?UTF-8\?B\?\S+\?= <jorg@example.com>""")))
        assertTrue(content.all { it.code < 0x80 })
    }

    @Test
    fun rendersAlternativeWithInlineImagesAndAttachments() {
        val image = byteArrayOf(1, 2, 3)
        val email = simple().copy {
            html = "<img src=\"cid:logo@example.com\">"
            embed("logo@example.com", image, "image/png")
            attach("report.pdf", "PDF".encodeToByteArray())
        }
        val content = email.render().content
        assertEquals("multipart/mixed", content.headers()["Content-Type"]!!.substringBefore(';'))
        val alternative = content.indexOf("Content-Type: multipart/alternative")
        val related = content.indexOf("Content-Type: multipart/related")
        val html = content.indexOf("Content-Type: text/html")
        val inline = content.indexOf("Content-ID: <logo@example.com>")
        val pdf = content.indexOf("Content-Type: application/pdf; name=\"report.pdf\"")
        assertTrue(alternative in 1 until related && related < html && html < inline && inline < pdf, content)
        assertTrue("Content-Disposition: inline; filename=\"logo\"" in content)
        assertTrue("Content-Disposition: attachment; filename=\"report.pdf\"" in content)
        assertTrue(Base64.encode(image) in content)

        // every boundary that is opened is closed exactly once
        val boundaries = Regex("boundary=\"([^\"]+)\"").findAll(content).map { it.groupValues[1] }.toList()
        assertEquals(3, boundaries.size)
        boundaries.forEach { assertEquals(1, Regex(Regex.escape("--$it--")).findAll(content).count()) }
    }

    @Test
    fun usesQuotedPrintableForNonAsciiText() {
        val content = simple().copy { text = "Grüße" }.eml
        assertTrue("Content-Transfer-Encoding: quoted-printable" in content)
        assertTrue("Gr=C3=BC=C3=9Fe" in content)
    }

    @Test
    fun flagsInternationalizedAddresses() {
        val rendered = simple().copy { to.clear(); to("jörg@bücher.de") }.render()
        assertTrue(rendered.requiresSmtpUtf8)
        assertTrue("To: jörg@bücher.de" in rendered.content)
    }

    @Test
    fun rendersCustomHeadersAndThreading() {
        val content = simple().copy {
            header("List-Unsubscribe", "<https://example.com/unsubscribe>")
            inReplyTo += "parent@example.com"
            references += listOf("root@example.com", "parent@example.com")
        }.eml
        val headers = content.headers()
        assertEquals("<https://example.com/unsubscribe>", headers["List-Unsubscribe"])
        assertEquals("<parent@example.com>", headers["In-Reply-To"])
        assertEquals("<root@example.com> <parent@example.com>", headers["References"])
    }
}
