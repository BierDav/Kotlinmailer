package at.quickme.kotlinmailer.email

import kotlin.time.Instant

/**
 * An immutable email message. Create one with [emailBuilder].
 */
@Suppress("LongParameterList") // only called by EmailBuilder
public class Email internal constructor(
    /** The author of the message. */
    public val from: EmailAddress,
    /** Visible primary recipients. */
    public val to: List<EmailAddress>,
    /** Visible carbon copy recipients. */
    public val cc: List<EmailAddress>,
    /** Hidden recipients; they are only part of the envelope and never rendered into headers. */
    public val bcc: List<EmailAddress>,
    /** Addresses replies should be sent to. */
    public val replyTo: List<EmailAddress>,
    /** The subject line. */
    public val subject: String?,
    /** The plain text body. */
    public val text: String?,
    /** The HTML body. */
    public val html: String?,
    /** Attachments, including inline ones referenced by `cid:` URLs from the HTML body. */
    public val attachments: List<Attachment>,
    /** Additional headers, rendered in the given order. */
    public val headers: List<Header>,
    /** The `Message-ID` without angle brackets, e.g. `1234@example.com`. */
    public val messageId: String,
    /** The `Message-ID`s this message replies to, without angle brackets. */
    public val inReplyTo: List<String>,
    /** The `Message-ID`s of the thread, without angle brackets. */
    public val references: List<String>,
    /** The `Date` header; the time of rendering is used if `null`. */
    public val date: Instant?,
) {
    /** All envelope recipients: [to], [cc] and [bcc] without duplicates. */
    public val recipients: List<EmailAddress>
        get() = (to + cc + bcc).distinctBy { it.address.lowercase() }

    /** Create a builder pre-filled with this email's content and a new [messageId]. */
    public fun newBuilder(): EmailBuilder = EmailBuilder().also { builder ->
        builder.from = from
        builder.to += to
        builder.cc += cc
        builder.bcc += bcc
        builder.replyTo += replyTo
        builder.subject = subject
        builder.text = text
        builder.html = html
        builder.attachments += attachments
        builder.headers += headers
        builder.inReplyTo += inReplyTo
        builder.references += references
        builder.date = date
    }

    override fun toString(): String = "Email(messageId=$messageId, from=$from, to=$to, subject=$subject)"
}

/** A custom header field. */
public class Header(public val name: String, public val value: String) {
    init {
        require(name.isNotEmpty() && name.all { it.isVisibleAscii() && it != ':' }) { "Invalid header name '$name'" }
        require(value.none { it == '\r' || it == '\n' }) { "Header '$name' must not contain line breaks" }
        require(name.lowercase() !in RESERVED_HEADERS) {
            "Header '$name' is managed by Kotlinmailer and cannot be set manually"
        }
    }

    override fun toString(): String = "$name: $value"

    internal companion object {
        val RESERVED_HEADERS = setOf(
            "from", "sender", "to", "cc", "bcc", "reply-to", "subject", "date", "message-id",
            "in-reply-to", "references", "mime-version", "content-type", "content-transfer-encoding",
            "content-disposition", "content-id",
        )
    }
}

/**
 * A file attached to an [Email].
 *
 * If [contentId] is set, the attachment is rendered inline and can be referenced
 * from the HTML body via `cid:<contentId>`, e.g. `<img src="cid:logo@example.com">`.
 */
public class Attachment(
    public val name: String,
    public val content: ByteArray,
    public val contentType: String = guessContentType(name),
    public val contentId: String? = null,
) {
    init {
        require(name.none { it == '\r' || it == '\n' }) { "Attachment name must not contain line breaks" }
        require(MIME_TYPE.matches(contentType)) { "Invalid content type '$contentType'" }
        require(contentId == null || contentId.isNotEmpty() && contentId.all { it.isVisibleAscii() && it !in "<>\"" }) {
            "Invalid content id '$contentId'"
        }
    }

    /** True if this attachment is embedded into the HTML body. */
    public val isInline: Boolean get() = contentId != null

    override fun toString(): String = "Attachment(name=$name, contentType=$contentType, size=${content.size})"

    public companion object {
        private val MIME_TYPE = Regex("^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$")

        private val CONTENT_TYPES = mapOf(
            "txt" to "text/plain", "html" to "text/html", "htm" to "text/html", "css" to "text/css",
            "csv" to "text/csv", "ics" to "text/calendar", "xml" to "application/xml",
            "json" to "application/json", "pdf" to "application/pdf", "zip" to "application/zip",
            "gz" to "application/gzip", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
            "gif" to "image/gif", "webp" to "image/webp", "svg" to "image/svg+xml", "ico" to "image/x-icon",
            "mp3" to "audio/mpeg", "mp4" to "video/mp4", "eml" to "message/rfc822",
            "doc" to "application/msword", "xls" to "application/vnd.ms-excel",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        )

        /** Guess a MIME type from a file name's extension, falling back to `application/octet-stream`. */
        public fun guessContentType(fileName: String): String =
            CONTENT_TYPES[fileName.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"
    }
}
