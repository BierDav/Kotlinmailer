package at.quickme.kotlinmailer.html

import at.quickme.kotlinmailer.email.EmailBuilder
import kotlinx.html.TagConsumer
import kotlinx.html.stream.appendHTML
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public typealias ContextlessHtmlBuilder = TagConsumer<StringBuilder>.() -> StringBuilder

/**
 * Set the HTML body using the kotlinx.html DSL.
 */
public inline fun EmailBuilder.withHTML(builder: ContextlessHtmlBuilder) {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    html = buildHtml(builder)
}

/**
 * Append to the HTML body using the kotlinx.html DSL.
 */
public inline fun EmailBuilder.appendHTML(builder: ContextlessHtmlBuilder) {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    appendHtml(buildHtml(builder))
}

/**
 * Prepend to the HTML body using the kotlinx.html DSL.
 */
public inline fun EmailBuilder.prependHTML(builder: ContextlessHtmlBuilder) {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    prependHtml(buildHtml(builder))
}

@PublishedApi
internal inline fun buildHtml(builder: ContextlessHtmlBuilder): String {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    return StringBuilder().appendHTML(prettyPrint = false).builder().toString()
}
