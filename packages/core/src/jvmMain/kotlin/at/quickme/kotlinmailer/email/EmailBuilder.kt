package at.quickme.kotlinmailer.email

import org.simplejavamail.api.email.Email
import org.simplejavamail.api.email.EmailPopulatingBuilder
import org.simplejavamail.email.EmailBuilder
import kotlin.contracts.contract

/**
 * Open a new email builder.
 * This function automatically builds the email and
 * returns it.
 */
inline fun emailBuilder(builder: EmailPopulatingBuilder.() -> Unit): Email {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return EmailBuilder.startingBlank().apply(builder).buildEmail()
}

/**
 * Copy this email and open a new email builder.
 * This function automatically builds the new email and
 * returns it.
 */
inline fun Email.copy(builder: EmailPopulatingBuilder.() -> Unit): Email {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return EmailBuilder.copying(this).apply(builder).buildEmail()
}

/**
 * Reply to this email.
 * This functions opens a new email builder, automatically builds
 * the new email and returns it.
 * @param from optional from recipient address
 * @param toAll
 */
inline fun Email.reply(
    from: String? = null,
    toAll: Boolean = false,
    builder: EmailPopulatingBuilder.() -> Unit
): Email {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    val reply = if (toAll) EmailBuilder.replyingToAll(this) else EmailBuilder.replyingTo(this)
    if (from != null)
        reply.from(from)
    return reply.apply(builder).buildEmail()
}

/**
 * Forward this email.
 * This functions opens a new email builder, automatically builds
 * the new email and returns it.
 * @param from optional from recipient address
 */
inline fun Email.forward(from: String? = null, builder: EmailPopulatingBuilder.() -> Unit = {}): Email {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return EmailBuilder.forwarding(this).apply { if (from != null) from(from) }.apply(builder).buildEmail()
}
