package at.quickme.kotlinmailer.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.quickme.kotlinmailer.email.Email
import at.quickme.kotlinmailer.email.emailBuilder
import at.quickme.kotlinmailer.html.withHTML
import at.quickme.kotlinmailer.jmap.JmapAuth
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h2
import kotlinx.html.hr
import kotlinx.html.p
import kotlinx.html.small

const val FASTMAIL_SESSION_URL = "https://api.fastmail.com/jmap/session"

enum class AuthMethod(val label: String) { Bearer("API token"), Basic("Username & password") }

/** Everything the user typed into the demo. */
class DemoForm {
    var sessionUrl by mutableStateOf(FASTMAIL_SESSION_URL)
    var authMethod by mutableStateOf(AuthMethod.Bearer)
    var token by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    var from by mutableStateOf("")
    var to by mutableStateOf("")
    var subject by mutableStateOf("Hello from Kotlinmailer")
    var text by mutableStateOf("This email was sent by a Kotlin/JS website, straight from the browser over JMAP.")
    var html by mutableStateOf(true)

    val recipients: List<String> get() = to.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    fun auth(): JmapAuth = when (authMethod) {
        AuthMethod.Bearer -> JmapAuth.Bearer(token.trim())
        AuthMethod.Basic -> JmapAuth.Basic(username.trim(), password)
    }

    fun email(): Email = emailBuilder {
        from(this@DemoForm.from.trim())
        recipients.forEach { to(it) }
        subject = this@DemoForm.subject
        text = this@DemoForm.text
        if (this@DemoForm.html) {
            val paragraphs = this@DemoForm.text.split("\n\n")
            withHTML {
                body {
                    h2 { +this@DemoForm.subject }
                    paragraphs.forEach { p { +it } }
                    hr()
                    small {
                        +"Sent with "
                        a("https://github.com/BierDav/Kotlinmailer") { +"Kotlinmailer" }
                        +" over JMAP"
                    }
                }
            }
        }
    }

    /** The Kotlin code doing the same as the demo. */
    fun code(): String = buildString {
        appendLine("val mailer = jmapMailer(${sessionUrl.trim().quoted()}) {")
        when (authMethod) {
            AuthMethod.Bearer -> appendLine("    bearer(\"<api-token>\")")
            AuthMethod.Basic -> appendLine("    basic(${username.trim().quoted()}, \"<password>\")")
        }
        appendLine("}")
        appendLine()
        appendLine("emailBuilder {")
        appendLine("    from(${from.trim().quoted()})")
        recipients.ifEmpty { listOf("") }.forEach { appendLine("    to(${it.quoted()})") }
        appendLine("    subject = ${subject.quoted()}")
        appendLine("    text = ${text.quoted()}")
        if (html) {
            appendLine("    withHTML {")
            appendLine("        body {")
            appendLine("            h2 { +subject }")
            appendLine("            p { +text }")
            appendLine("        }")
            appendLine("    }")
        }
        append("}.send(mailer)")
    }

    private fun String.quoted(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("$", "\\$") + "\""
}
