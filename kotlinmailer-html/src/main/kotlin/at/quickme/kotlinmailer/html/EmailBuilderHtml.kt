package at.quickme.kotlinmailer.html

import kotlinx.html.TagConsumer
import kotlinx.html.stream.appendHTML
import org.simplejavamail.api.email.EmailPopulatingBuilder

private typealias ContextlessHtmlBuilder = TagConsumer<StringBuilder>.() -> StringBuilder

/**
 * Set the html of the message.
 */
fun EmailPopulatingBuilder.withHTML(builder: ContextlessHtmlBuilder): EmailPopulatingBuilder =
    withHTMLText(builder.build())

/**
 * Append html to the message.
 */
fun EmailPopulatingBuilder.appendHTML(builder: ContextlessHtmlBuilder): EmailPopulatingBuilder =
    appendTextHTML(builder.build())

/**
 * Prepend html to the message.
 */
fun EmailPopulatingBuilder.prependHTML(builder: ContextlessHtmlBuilder): EmailPopulatingBuilder =
    prependTextHTML(builder.build())

@Suppress("NOTHING_TO_INLINE")
inline fun ContextlessHtmlBuilder.build() = this(StringBuilder().appendHTML()).toString()
