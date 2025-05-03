package at.quickme.kotlinmailer.html

import kotlinx.html.TagConsumer
import kotlinx.html.stream.appendHTML
import org.simplejavamail.api.email.EmailPopulatingBuilder
import kotlin.contracts.contract

typealias ContextlessHtmlBuilder = TagConsumer<StringBuilder>.() -> StringBuilder

/**
 * Set the html of the message.
 */
fun EmailPopulatingBuilder.withHTML(builder: ContextlessHtmlBuilder): EmailPopulatingBuilder {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return withHTMLText(build(builder))
}

/**
 * Append html to the message.
 */
fun EmailPopulatingBuilder.appendHTML(builder: ContextlessHtmlBuilder): EmailPopulatingBuilder {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return appendTextHTML(build(builder))
}

/**
 * Prepend html to the message.
 */
fun EmailPopulatingBuilder.prependHTML(builder: ContextlessHtmlBuilder): EmailPopulatingBuilder {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return prependTextHTML(build(builder))
}

@Suppress("NOTHING_TO_INLINE")
private inline fun build(builder: ContextlessHtmlBuilder): String {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return builder(StringBuilder().appendHTML()).toString()
}
