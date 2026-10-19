package at.quickme.kotlinmailer.mime

import at.quickme.kotlinmailer.email.Email

/**
 * An [Email] rendered to an RFC 5322 message with CRLF line endings.
 */
public class RenderedEmail internal constructor(
    /** The complete message including headers. */
    public val content: String,
    /** True if the message contains internationalized addresses (RFC 6532), which need `SMTPUTF8`. */
    public val requiresSmtpUtf8: Boolean,
    /** True if the message contains 8-bit data, which needs `8BITMIME`. */
    public val requires8BitMime: Boolean,
) {
    /** The message encoded as UTF-8. */
    public val bytes: ByteArray by lazy { content.encodeToByteArray() }
}
