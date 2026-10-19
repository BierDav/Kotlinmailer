package at.quickme.kotlinmailer.html

import at.quickme.kotlinmailer.email.emailBuilder
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailBuilderHtmlTest {
    @Test
    fun buildsHtmlBodies() {
        val items = listOf("banana", "apple")
        val email = emailBuilder {
            from("shop@example.com")
            to("customer@example.com")
            withHTML {
                body {
                    h1 { +"Order received" }
                    ul { items.forEach { li { +it } } }
                }
            }
            appendHTML { p { +"Thanks & bye" } }
            prependHTML { p { +"Hi" } }
        }
        assertEquals(
            "<p>Hi</p><body><h1>Order received</h1><ul><li>banana</li><li>apple</li></ul></body>" +
                "<p>Thanks &amp; bye</p>",
            email.html,
        )
    }
}
