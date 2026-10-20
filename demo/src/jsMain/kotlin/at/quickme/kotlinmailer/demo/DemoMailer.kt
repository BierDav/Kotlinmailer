package at.quickme.kotlinmailer.demo

import at.quickme.kotlinmailer.delivery.SendReceipt
import at.quickme.kotlinmailer.email.Email
import at.quickme.kotlinmailer.jmap.JmapAuth
import at.quickme.kotlinmailer.jmap.JmapConfig
import at.quickme.kotlinmailer.jmap.JmapMailer
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.content.TextContent

/** One HTTP exchange with the JMAP server, shown in the protocol log. */
class Exchange(val method: String, val path: String, val status: Int, val jmapMethods: List<String>)

private val methodCall = Regex("""\["([A-Za-z]+/[A-Za-z]+)"""")

/** Sends [email] with a fresh [JmapMailer], reporting every HTTP request to [onExchange]. */
suspend fun sendOverJmap(
    sessionUrl: String,
    auth: JmapAuth,
    email: Email,
    onExchange: (Exchange) -> Unit,
): SendReceipt {
    val logger = createClientPlugin("JmapLog") {
        onResponse { response ->
            val request = response.call.request
            val body = (request.content as? TextContent)?.text.orEmpty()
            onExchange(
                Exchange(
                    method = request.method.value,
                    path = request.url.encodedPath,
                    status = response.status.value,
                    jmapMethods = methodCall.findAll(body).map { it.groupValues[1] }.toList(),
                ),
            )
        }
    }
    val client = HttpClient(Js) { install(logger) }
    val mailer = JmapMailer(JmapConfig(sessionUrl, auth, httpClient = client))
    return try {
        mailer.send(email)
    } finally {
        mailer.close()
        client.close()
    }
}
