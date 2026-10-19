package at.quickme.kotlinmailer.email

import at.quickme.kotlinmailer.mime.eml
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Instant
import kotlin.uuid.Uuid

@DslMarker
public annotation class EmailDsl

/** Mutable builder for [Email]; usually used through [emailBuilder]. */
@EmailDsl
@Suppress("TooManyFunctions") // one function per DSL keyword
public class EmailBuilder {
    public var from: EmailAddress? = null
    public val to: MutableList<EmailAddress> = mutableListOf()
    public val cc: MutableList<EmailAddress> = mutableListOf()
    public val bcc: MutableList<EmailAddress> = mutableListOf()
    public val replyTo: MutableList<EmailAddress> = mutableListOf()
    public var subject: String? = null
    public var text: String? = null
    public var html: String? = null
    public val attachments: MutableList<Attachment> = mutableListOf()
    public val headers: MutableList<Header> = mutableListOf()

    /** The `Message-ID` without angle brackets; generated from the sender's domain if `null`. */
    public var messageId: String? = null
    public val inReplyTo: MutableList<String> = mutableListOf()
    public val references: MutableList<String> = mutableListOf()

    /** The `Date` header; the time of rendering is used if `null`. */
    public var date: Instant? = null

    /** Set the sender, either `john@example.com` or `John <john@example.com>` if [name] is `null`. */
    public fun from(address: String, name: String? = null) {
        from = address(address, name)
    }

    public fun to(address: String, name: String? = null) {
        to += address(address, name)
    }

    public fun to(vararg addresses: EmailAddress) {
        to += addresses
    }

    public fun cc(address: String, name: String? = null) {
        cc += address(address, name)
    }

    public fun cc(vararg addresses: EmailAddress) {
        cc += addresses
    }

    public fun bcc(address: String, name: String? = null) {
        bcc += address(address, name)
    }

    public fun bcc(vararg addresses: EmailAddress) {
        bcc += addresses
    }

    public fun replyTo(address: String, name: String? = null) {
        replyTo += address(address, name)
    }

    public fun replyTo(vararg addresses: EmailAddress) {
        replyTo += addresses
    }

    public fun appendText(value: String) {
        text = (text ?: "") + value
    }

    public fun prependText(value: String) {
        text = value + (text ?: "")
    }

    public fun appendHtml(value: String) {
        html = (html ?: "") + value
    }

    public fun prependHtml(value: String) {
        html = value + (html ?: "")
    }

    /** Attach a file. */
    public fun attach(name: String, content: ByteArray, contentType: String = Attachment.guessContentType(name)) {
        attachments += Attachment(name, content, contentType)
    }

    /** Embed a file that the HTML body references via `cid:<contentId>`. */
    public fun embed(
        contentId: String,
        content: ByteArray,
        contentType: String,
        name: String = contentId.substringBefore('@'),
    ) {
        attachments += Attachment(name, content, contentType, contentId)
    }

    /** Add a custom header, e.g. `List-Unsubscribe`. */
    public fun header(name: String, value: String) {
        headers += Header(name, value)
    }

    public fun build(): Email {
        val sender = checkNotNull(from) { "An email requires a 'from' address" }
        check(to.isNotEmpty() || cc.isNotEmpty() || bcc.isNotEmpty()) { "An email requires at least one recipient" }
        subject?.let { s -> require(s.none { it == '\r' || it == '\n' }) { "Subject must not contain line breaks" } }
        val ids = inReplyTo + references + listOfNotNull(messageId)
        require(ids.all(::isValidMessageId)) { "Invalid message id in $ids" }

        return Email(
            from = sender,
            to = to.toList(),
            cc = cc.toList(),
            bcc = bcc.toList(),
            replyTo = replyTo.toList(),
            subject = subject,
            text = text,
            html = html,
            attachments = attachments.toList(),
            headers = headers.toList(),
            messageId = messageId ?: generateMessageId(sender),
            inReplyTo = inReplyTo.toList(),
            references = references.toList(),
            date = date,
        )
    }

    private fun address(address: String, name: String?) =
        if (name == null) EmailAddress.parse(address) else EmailAddress(address, name)
}

private fun isValidMessageId(id: String) =
    id.isNotEmpty() && id.all { it.isVisibleAscii() && it !in "<>" } && '@' in id

private fun generateMessageId(from: EmailAddress): String {
    val domain = from.domain.takeIf { d -> d.isAscii() && !d.startsWith('[') } ?: "kotlinmailer.local"
    return "${Uuid.random().toHexString()}@$domain"
}

/**
 * Build a new [Email].
 */
public inline fun emailBuilder(builder: EmailBuilder.() -> Unit): Email {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    return EmailBuilder().apply(builder).build()
}

/**
 * Copy this email, modify it with [builder] and build it with a new message id.
 */
public inline fun Email.copy(builder: EmailBuilder.() -> Unit = {}): Email {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    return newBuilder().apply(builder).build()
}

/**
 * Reply to this email. The new email is addressed to the [Email.replyTo] addresses (or [Email.from]),
 * keeps the thread headers and quotes the original body.
 *
 * @param from the sender of the reply
 * @param toAll also reply to all other [Email.to] and [Email.cc] recipients
 */
public inline fun Email.reply(
    from: EmailAddress,
    toAll: Boolean = false,
    builder: EmailBuilder.() -> Unit = {},
): Email {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    return replyBuilder(from, toAll).apply(builder).build()
}

/**
 * Forward this email. The original is attached as `message/rfc822`.
 *
 * @param from the sender of the forwarded email
 */
public inline fun Email.forward(from: EmailAddress, builder: EmailBuilder.() -> Unit = {}): Email {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    return forwardBuilder(from).apply(builder).build()
}

@PublishedApi
internal fun Email.replyBuilder(from: EmailAddress, toAll: Boolean): EmailBuilder {
    val original = this
    return EmailBuilder().apply {
        this.from = from
        val primary = original.replyTo.ifEmpty { listOf(original.from) }
        to += primary
        if (toAll) {
            val excluded = (primary + from).map { it.address.lowercase() }.toSet()
            cc += (original.to + original.cc)
                .filter { it.address.lowercase() !in excluded }
                .distinctBy { it.address.lowercase() }
        }
        subject = prefixed("Re:", original.subject)
        inReplyTo += original.messageId
        references += original.references.ifEmpty { original.inReplyTo } + original.messageId
        original.text?.let {
            text = "\r\n\r\n${original.from} wrote:\r\n" + it.lines().joinToString("\r\n") { line -> "> $line" }
        }
        original.html?.let {
            html = "<br><br><div>${escapeHtml(original.from.toString())} wrote:</div><blockquote>$it</blockquote>"
        }
    }
}

@PublishedApi
internal fun Email.forwardBuilder(from: EmailAddress): EmailBuilder {
    val original = this
    return EmailBuilder().apply {
        this.from = from
        subject = prefixed("Fwd:", original.subject)
        val fileName = original.subject?.takeIf { it.isNotBlank() } ?: "forwarded"
        attach("$fileName.eml", original.eml.encodeToByteArray(), "message/rfc822")
    }
}

private fun prefixed(prefix: String, subject: String?): String {
    val value = subject.orEmpty()
    return if (value.startsWith(prefix, ignoreCase = true)) value else "$prefix $value".trimEnd()
}

private fun escapeHtml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
