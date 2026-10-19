package at.quickme.kotlinmailer.mime

import at.quickme.kotlinmailer.email.EmailAddress
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MimeEncodingTest {
    @Test
    fun quotedPrintableKeepsAsciiReadable() {
        assertEquals("Hello World", quotedPrintable("Hello World"))
        assertEquals("a=3Db", quotedPrintable("a=b"))
    }

    @Test
    fun quotedPrintableEncodesUtf8AndTrailingWhitespace() {
        assertEquals("Gr=C3=BC=C3=9Fe=20\r\nnext", quotedPrintable("Grüße \nnext"))
    }

    @Test
    fun quotedPrintableUsesSoftLineBreaks() {
        val text = "x".repeat(200) + "\n" + "ü".repeat(100)
        val encoded = quotedPrintable(text)
        encoded.split("\r\n").forEach { assertTrue(it.length <= 76, "line too long: $it") }
        assertEquals(text.replace("\n", "\r\n"), decodeQuotedPrintable(encoded))
    }

    @Test
    fun encodedWordsAreShortAndRoundTrip() {
        val text = "Привет мир, this is a long subject with emojis 😀😀😀 and umlauts äöü ".repeat(3)
        val encoded = encodeWords(text)
        val words = encoded.split(' ')
        words.forEach {
            assertTrue(it.length <= 75, "word too long: $it")
            assertTrue(it.startsWith("=?UTF-8?B?") && it.endsWith("?="))
        }
        val decoded = words.joinToString("") {
            Base64.decode(it.removePrefix("=?UTF-8?B?").removeSuffix("?=")).decodeToString()
        }
        assertEquals(text, decoded)
    }

    @Test
    fun foldsLongHeadersAtWhitespace() {
        val value = (1..40).joinToString(" ") { "word$it" }
        val folded = foldHeader("Subject", value)
        val lines = folded.removeSuffix("\r\n").split("\r\n")
        assertTrue(lines.size > 1)
        lines.forEach { assertTrue(it.length <= 78, "line too long: $it") }
        assertEquals("Subject: $value", lines.joinToString(""))
    }

    @Test
    fun phrasesAreQuotedOrEncodedWhenNeeded() {
        assertEquals("John Doe", phrase("John Doe"))
        assertEquals("\"Doe, John\"", phrase("Doe, John"))
        assertEquals("\"say \\\"hi\\\"\"", phrase("say \"hi\""))
        assertEquals("=?UTF-8?B?SsO2cmc=?=", phrase("Jörg"))
    }

    @Test
    fun mailboxRendersNameAndAddress() {
        assertEquals("\"Doe, John\" <john@example.com>", mailbox(EmailAddress("john@example.com", "Doe, John")))
        assertEquals("john@example.com", mailbox(EmailAddress("john@example.com")))
    }

    @Test
    fun parametersUseRfc2231ForNonAsciiAndLongValues() {
        assertEquals("filename=\"report.pdf\"", parameter("filename", "report.pdf"))
        assertEquals("filename*=UTF-8''Gr%C3%BC%C3%9Fe.txt", parameter("filename", "Grüße.txt"))
        val long = parameter("filename", "a".repeat(100) + ".txt")
        assertTrue(long.startsWith("filename*0*=UTF-8''"))
        assertTrue("; filename*1*=" in long)
    }

    @Test
    fun formatsRfc5322Dates() {
        val instant = kotlin.time.Instant.parse("2024-03-05T07:08:09Z")
        assertEquals("Tue, 5 Mar 2024 07:08:09 +0000", formatDate(instant, kotlinx.datetime.TimeZone.UTC))
        assertEquals("Tue, 5 Mar 2024 08:08:09 +0100", formatDate(instant, kotlinx.datetime.TimeZone.of("+01:00")))
    }
}

internal fun decodeQuotedPrintable(value: String): String {
    val bytes = mutableListOf<Byte>()
    val text = value.replace("=\r\n", "")
    var i = 0
    while (i < text.length) {
        if (text[i] == '=') {
            bytes += text.substring(i + 1, i + 3).toInt(16).toByte()
            i += 3
        } else {
            bytes += text[i].code.toByte()
            i++
        }
    }
    return bytes.toByteArray().decodeToString()
}
