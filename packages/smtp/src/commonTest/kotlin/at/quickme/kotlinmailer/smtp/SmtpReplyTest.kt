package at.quickme.kotlinmailer.smtp

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmtpReplyTest {
    private class LinesSocket(lines: List<String>) : SmtpSocket {
        private val lines = ArrayDeque(lines)
        override val isSecure = false
        override suspend fun readLine() = lines.removeFirstOrNull()
        override suspend fun write(data: ByteArray) = Unit
        override suspend fun flush() = Unit
        override suspend fun startTls() = Unit
        override fun close() = Unit
    }

    private suspend fun read(vararg lines: String) = SmtpReply.read(LinesSocket(lines.toList()))

    @Test
    fun parsesMultilineReplies() = runTest {
        val reply = read(
            "250-mail.example.com",
            "250-SIZE 35882577",
            "250-AUTH LOGIN PLAIN XOAUTH2",
            "250-AUTH=LOGIN",
            "250 8BITMIME",
        )
        assertEquals(250, reply.code)
        val extensions = SmtpExtensions.parse(reply)
        assertEquals(35882577L, extensions.maxSize)
        assertEquals(setOf("LOGIN", "PLAIN", "XOAUTH2"), extensions.authMechanisms)
        assertTrue("8bitmime" in extensions)
    }

    @Test
    fun parsesCodeOnlyAndEnhancedStatus() = runTest {
        assertEquals(250, read("250").code)
        assertEquals("5.7.1", read("550 5.7.1 Relaying denied").enhancedStatusCode)
        assertNull(read("250 Ok").enhancedStatusCode)
        assertNull(SmtpExtensions.parse(read("250-x", "250 SIZE 0")).maxSize)
    }

    @Test
    fun rejectsMalformedReplies() = runTest {
        assertFailsWith<SmtpException> { read("hello") }
        assertFailsWith<SmtpException> { read("25") }
        assertFailsWith<SmtpException> { read("250x") }
        assertFailsWith<SmtpException> { read("250-a", "251 b") }
        assertFailsWith<SmtpException> { read("250-a") }
    }

    @Test
    fun splitsLinesFromChunks() {
        val buffer = LineBuffer()
        buffer.append("220 hel".encodeToByteArray())
        assertNull(buffer.pollLine())
        buffer.append("lo\r\n250 ok\nrest".encodeToByteArray())
        assertEquals("220 hello", buffer.pollLine())
        assertEquals("250 ok", buffer.pollLine())
        assertNull(buffer.pollLine())
        assertTrue(!buffer.isEmpty())
    }
}
