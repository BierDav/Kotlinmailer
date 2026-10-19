package at.quickme.kotlinmailer.email

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmailAddressTest {
    @Test
    fun parsesBareAddress() {
        val address = EmailAddress.parse(" john.doe@example.com ")
        assertEquals("john.doe@example.com", address.address)
        assertNull(address.name)
        assertEquals("john.doe", address.localPart)
        assertEquals("example.com", address.domain)
    }

    @Test
    fun parsesNameAddr() {
        val address = EmailAddress.parse("John Doe <john@example.com>")
        assertEquals("john@example.com", address.address)
        assertEquals("John Doe", address.name)
    }

    @Test
    fun parsesQuotedDisplayName() {
        val address = EmailAddress.parse("\"Doe, John \\\"JD\\\"\" <john@example.com>")
        assertEquals("Doe, John \"JD\"", address.name)
    }

    @Test
    fun acceptsSpecialButValidAddresses() {
        listOf(
            "user+tag@example.com",
            "\"john doe\"@example.com",
            "\"quote \\\" and backslash \\\\\"@example.com",
            "admin@[192.0.2.1]",
            "admin@[IPv6:2001:db8::1]",
            "o'brien@sub.example.co.uk",
            "jörg@bücher.de",
        ).forEach { EmailAddress(it) }
    }

    @Test
    fun rejectsInvalidAddresses() {
        listOf(
            "", "plainaddress", "@example.com", "john@", "john..doe@example.com", ".john@example.com",
            "john@-example.com", "john@example..com", "john doe@example.com", "john@exa mple.com",
            "${"a".repeat(65)}@example.com",
            "\"unescaped \" quote\"@example.com", "\"trailing backslash\\\"@example.com",
            "\"line\r\nbreak\"@example.com",
            "admin@[]", "admin@[192.0.2.1]]", "admin@[a\\b]",
        ).forEach { assertFailsWith<IllegalArgumentException>(it) { EmailAddress(it) } }
    }

    @Test
    fun rejectsLineBreaksInNames() {
        assertFailsWith<IllegalArgumentException> { EmailAddress("john@example.com", "John\r\nBcc: evil@example.com") }
    }

    @Test
    fun detectsInternationalizedAddresses() {
        assertTrue(EmailAddress("jörg@example.com").isInternationalized)
        assertFalse(EmailAddress("joerg@example.com", "Jörg").isInternationalized)
    }

    @Test
    fun comparesAddressesCaseInsensitively() {
        assertEquals(EmailAddress("John@Example.com"), EmailAddress("john@example.com"))
    }
}
