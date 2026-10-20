package at.quickme.kotlinmailer.demo.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import at.quickme.kotlinmailer.delivery.SendReceipt
import at.quickme.kotlinmailer.demo.AuthMethod
import at.quickme.kotlinmailer.demo.DemoForm
import at.quickme.kotlinmailer.demo.Exchange
import at.quickme.kotlinmailer.demo.sendOverJmap
import at.quickme.kotlinmailer.jmap.JmapException
import at.quickme.kotlinmailer.mime.render
import com.varabyte.kobweb.core.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.target
import org.jetbrains.compose.web.attributes.ATarget
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.B
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Footer
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Header
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Main
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import org.jetbrains.compose.web.dom.Ul

private sealed interface SendState {
    data object Idle : SendState
    data object Sending : SendState
    class Sent(val receipt: SendReceipt) : SendState
    class Failed(val message: String) : SendState
}

@Page
@Composable
fun HomePage() {
    val form = remember { DemoForm() }
    val exchanges = remember { mutableStateListOf<Exchange>() }
    var state by remember { mutableStateOf<SendState>(SendState.Idle) }
    var mime by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun preview() {
        mime = try {
            form.email().render().content
        } catch (e: IllegalArgumentException) {
            state = SendState.Failed(e.message ?: "Invalid email")
            null
        }
    }

    fun send() = scope.launch {
        exchanges.clear()
        state = SendState.Sending
        state = try {
            val email = form.email()
            mime = email.render().content
            SendState.Sent(sendOverJmap(form.sessionUrl.trim(), form.auth(), email) { exchanges += it })
        } catch (e: CancellationException) {
            throw e
        } catch (e: JmapException) {
            SendState.Failed(listOfNotNull(e.message, e.type?.let { "(type: $it)" }).joinToString(" "))
        } catch (e: IllegalArgumentException) {
            SendState.Failed(e.message ?: "Invalid input")
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            SendState.Failed(
                "${e.message ?: e::class.simpleName}. If this is a network error, check the session URL and that " +
                    "the server allows cross-origin (CORS) requests from this site.",
            )
        }
    }

    Header(attrs = { classes("hero") }) {
        Img("kotlinmailer_logo_light.svg", "Kotlinmailer") { classes("logo", "logo-light") }
        Img("kotlinmailer_logo_dark.svg", "Kotlinmailer") { classes("logo", "logo-dark") }
        H1 { Text("Send emails from the browser") }
        P(attrs = { classes("lead") }) {
            Text("This page is a ")
            A("https://kobweb.varabyte.com") { Text("Kobweb") }
            Text(" app compiled to JavaScript. It builds the email with Kotlinmailer and sends it over ")
            A("https://jmap.io") { Text("JMAP") }
            Text(", directly from your browser to your mail server. There is no backend.")
        }
    }

    Main(attrs = { classes("grid") }) {
        Div(attrs = { classes("column") }) {
            Section(attrs = { classes("card") }) {
                H2 { Text("1. JMAP server") }
                Field("Session URL") {
                    Input(InputType.Url) {
                        value(form.sessionUrl)
                        onInput { form.sessionUrl = it.value }
                    }
                }
                Div(attrs = { classes("tabs") }) {
                    AuthMethod.entries.forEach { method ->
                        Button(attrs = {
                            classes("tab")
                            if (form.authMethod == method) classes("active")
                            onClick { form.authMethod = method }
                        }) { Text(method.label) }
                    }
                }
                when (form.authMethod) {
                    AuthMethod.Bearer -> Field("API token") {
                        Input(InputType.Password) {
                            value(form.token)
                            placeholder("fmu1-...")
                            onInput { form.token = it.value }
                        }
                    }
                    AuthMethod.Basic -> {
                        Field("Username") {
                            Input(InputType.Text) {
                                value(form.username)
                                onInput { form.username = it.value }
                            }
                        }
                        Field("Password") {
                            Input(InputType.Password) {
                                value(form.password)
                                onInput { form.password = it.value }
                            }
                        }
                    }
                }
                P(attrs = { classes("hint") }) {
                    Text("For Fastmail, create an API token with the ")
                    B { Text("Email") }
                    Text(" and ")
                    B { Text("Email submission") }
                    Text(" scopes under Settings → Privacy & Security → API tokens. Your credentials are only sent " +
                        "to the server above and are not stored.")
                }
            }

            Section(attrs = { classes("card") }) {
                H2 { Text("2. Message") }
                Field("From") {
                    Input(InputType.Email) {
                        value(form.from)
                        placeholder("you@fastmail.com")
                        onInput { form.from = it.value }
                    }
                }
                Field("To (comma separated)") {
                    Input(InputType.Text) {
                        value(form.to)
                        placeholder("friend@example.com")
                        onInput { form.to = it.value }
                    }
                }
                Field("Subject") {
                    Input(InputType.Text) {
                        value(form.subject)
                        onInput { form.subject = it.value }
                    }
                }
                Field("Text") {
                    TextArea(form.text) {
                        onInput { form.text = it.value }
                    }
                }
                Label(attrs = { classes("checkbox") }) {
                    Input(InputType.Checkbox) {
                        checked(form.html)
                        onInput { form.html = it.value }
                    }
                    Text("Add an HTML body built with kotlinx.html")
                }
                Div(attrs = { classes("actions") }) {
                    Button(attrs = {
                        classes("secondary")
                        onClick { preview() }
                    }) { Text("Preview MIME") }
                    Button(attrs = {
                        classes("primary")
                        if (state == SendState.Sending) disabled()
                        onClick { send() }
                    }) { Text(if (state == SendState.Sending) "Sending…" else "Send email") }
                }
            }
        }

        Div(attrs = { classes("column") }) {
            Section(attrs = { classes("card") }) {
                H2 { Text("Kotlin code") }
                Pre(attrs = { classes("code") }) { Code { Text(form.code()) } }
            }

            Section(attrs = { classes("card") }) {
                H2 { Text("Result") }
                when (val current = state) {
                    SendState.Idle -> P(attrs = { classes("hint") }) { Text("Nothing sent yet.") }
                    SendState.Sending -> P(attrs = { classes("status") }) { Text("Talking to the JMAP server…") }
                    is SendState.Sent -> Div(attrs = { classes("status", "success") }) {
                        P { Text("Sent to ${current.receipt.acceptedRecipients.joinToString { it.address }}") }
                        P { Code { Text("Message-ID: <${current.receipt.messageId}>") } }
                        current.receipt.serverResponse?.let { P { Code { Text("Submission: $it") } } }
                    }
                    is SendState.Failed -> P(attrs = { classes("status", "error") }) { Text(current.message) }
                }
                if (exchanges.isNotEmpty()) {
                    Ul(attrs = { classes("log") }) {
                        exchanges.forEach { exchange ->
                            Li {
                                Span(attrs = { classes("verb") }) { Text(exchange.method) }
                                Span(attrs = { classes("path") }) { Text(exchange.path) }
                                Span(attrs = {
                                    classes("code-status", if (exchange.status < 400) "ok" else "bad")
                                }) { Text(exchange.status.toString()) }
                                if (exchange.jmapMethods.isNotEmpty()) {
                                    Span(attrs = { classes("methods") }) {
                                        Text(exchange.jmapMethods.joinToString(", "))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            mime?.let {
                Section(attrs = { classes("card") }) {
                    H2 { Text("Rendered MIME message") }
                    Pre(attrs = { classes("code", "mime") }) { Code { Text(it) } }
                }
            }
        }
    }

    Footer {
        Text("Why JMAP? Browsers cannot open raw TCP connections, so SMTP is not available on the web. ")
        Text("JMAP only needs HTTPS. ")
        A("https://bierdav.github.io/Kotlinmailer/", attrs = { target(ATarget.Blank) }) { Text("Documentation") }
        Text(" · ")
        A("https://github.com/BierDav/Kotlinmailer/tree/main/demo", attrs = { target(ATarget.Blank) }) {
            Text("Source of this page")
        }
    }
}

@Composable
private fun Field(label: String, input: @Composable () -> Unit) {
    Label(attrs = { classes("field") }) {
        Span { Text(label) }
        input()
    }
}
