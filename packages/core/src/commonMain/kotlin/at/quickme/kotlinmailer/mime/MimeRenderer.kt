package at.quickme.kotlinmailer.mime

import at.quickme.kotlinmailer.email.Attachment
import at.quickme.kotlinmailer.email.Email
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.format.format
import kotlinx.datetime.offsetAt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Render this email to a MIME message.
 *
 * @param date the value of the `Date` header if [Email.date] is not set
 */
public fun Email.render(date: Instant = Clock.System.now()): RenderedEmail =
    MimeRenderer(this).render(this.date ?: date)

/** This email as `.eml` (RFC 5322) string. */
public val Email.eml: String get() = render().content

private val RFC5322_DATE = DateTimeComponents.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    chars(", ")
    day(Padding.NONE)
    char(' ')
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    year()
    char(' ')
    hour()
    char(':')
    minute()
    char(':')
    second()
    char(' ')
    offset(UtcOffset.Formats.FOUR_DIGITS)
}

internal fun formatDate(instant: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    RFC5322_DATE.format { setDateTimeOffset(instant, zone.offsetAt(instant)) }

private sealed interface Part

private class Leaf(val headers: List<Pair<String, String>>, val body: String) : Part

private class Multipart(val subtype: String, val parts: List<Part>) : Part {
    val boundary = "=_km_${Uuid.random().toHexString()}"
}

private class MimeRenderer(private val email: Email) {
    private var eightBit = false

    fun render(date: Instant): RenderedEmail {
        val out = StringBuilder()
        fun header(name: String, value: String) = out.append(foldHeader(name, value))

        header("Date", formatDate(date))
        header("From", mailbox(email.from))
        if (email.replyTo.isNotEmpty()) header("Reply-To", addressList(email.replyTo))
        if (email.to.isNotEmpty()) header("To", addressList(email.to))
        if (email.cc.isNotEmpty()) header("Cc", addressList(email.cc))
        email.subject?.let { header("Subject", unstructured(it)) }
        header("Message-ID", "<${email.messageId}>")
        if (email.inReplyTo.isNotEmpty()) header("In-Reply-To", email.inReplyTo.joinToString(" ") { "<$it>" })
        if (email.references.isNotEmpty()) header("References", email.references.joinToString(" ") { "<$it>" })
        email.headers.forEach { header(it.name, unstructured(it.value)) }
        header("MIME-Version", "1.0")

        writePart(out, structure())
        out.append(CRLF)

        val utf8 = (listOf(email.from) + email.replyTo + email.recipients).any { it.isInternationalized }
        return RenderedEmail(out.toString(), requiresSmtpUtf8 = utf8, requires8BitMime = eightBit || utf8)
    }

    private fun structure(): Part {
        val text = email.text?.let { textPart("plain", it) }
        val html = email.html?.let { textPart("html", it) }
        val (inline, regular) = email.attachments.partition { it.isInline && html != null }
        val inlineParts = inline.map(::attachmentPart)
        val htmlWithInline = when {
            html == null -> null
            inlineParts.isEmpty() -> html
            else -> Multipart("related", listOf(html) + inlineParts)
        }

        val body = when {
            text != null && htmlWithInline != null -> Multipart("alternative", listOf(text, htmlWithInline))
            htmlWithInline != null -> htmlWithInline
            else -> text ?: textPart("plain", "")
        }
        return if (regular.isEmpty()) body else Multipart("mixed", listOf(body) + regular.map(::attachmentPart))
    }

    private fun textPart(subtype: String, content: String): Leaf {
        val sevenBit = canUse7Bit(content)
        return Leaf(
            listOf(
                "Content-Type" to "text/$subtype; charset=UTF-8",
                "Content-Transfer-Encoding" to if (sevenBit) "7bit" else "quoted-printable",
            ),
            if (sevenBit) content.normalizedLines().joinToString(CRLF) else quotedPrintable(content),
        )
    }

    private fun attachmentPart(attachment: Attachment): Leaf {
        val (encoding, body) = if (attachment.contentType.equals("message/rfc822", ignoreCase = true)) {
            // RFC 2046 5.2.1 forbids base64 for message/rfc822
            val message = attachment.content.decodeToString()
            val sevenBit = canUse7Bit(message)
            if (!sevenBit) eightBit = true
            (if (sevenBit) "7bit" else "8bit") to message.normalizedLines().joinToString(CRLF)
        } else {
            "base64" to base64Lines(attachment.content)
        }
        val disposition = if (attachment.isInline) "inline" else "attachment"
        return Leaf(
            listOfNotNull(
                "Content-Type" to "${attachment.contentType}; ${parameter("name", attachment.name)}",
                "Content-Transfer-Encoding" to encoding,
                "Content-Disposition" to "$disposition; ${parameter("filename", attachment.name)}",
                attachment.contentId?.let { "Content-ID" to "<$it>" },
            ),
            body,
        )
    }

    private fun writePart(out: StringBuilder, part: Part) {
        when (part) {
            is Leaf -> {
                part.headers.forEach { (name, value) -> out.append(foldHeader(name, value)) }
                out.append(CRLF).append(part.body)
            }

            is Multipart -> {
                out.append(foldHeader("Content-Type", "multipart/${part.subtype}; boundary=\"${part.boundary}\""))
                out.append(CRLF)
                for (child in part.parts) {
                    out.append("--").append(part.boundary).append(CRLF)
                    writePart(out, child)
                    out.append(CRLF)
                }
                out.append("--").append(part.boundary).append("--")
            }
        }
    }
}
