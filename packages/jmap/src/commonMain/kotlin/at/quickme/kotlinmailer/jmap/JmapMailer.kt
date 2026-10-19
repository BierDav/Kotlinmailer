package at.quickme.kotlinmailer.jmap

import at.quickme.kotlinmailer.delivery.Mailer
import at.quickme.kotlinmailer.delivery.SendReceipt
import at.quickme.kotlinmailer.email.Email
import at.quickme.kotlinmailer.mime.render
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

/**
 * Sends emails via JMAP (RFC 8620, RFC 8621): the rendered message is uploaded, imported into the
 * Drafts mailbox and submitted with `EmailSubmission/set`, which moves it to the Sent mailbox.
 *
 * Works on every platform with a Ktor client engine, including browsers.
 */
public class JmapMailer(public val config: JmapConfig) : Mailer {
    private data class Account(
        val session: Session,
        val apiUrl: String,
        val uploadUrl: String,
        val accountId: String,
        val drafts: String?,
        val sent: String?,
        val identities: List<Identity>,
    )

    private val ownsClient = config.httpClient == null
    private val client: HttpClient by lazy { config.httpClient ?: HttpClient() }
    private val lock = Mutex()
    private var account: Account? = null

    override suspend fun send(email: Email): SendReceipt {
        val account = account()
        val identity = identityFor(account, email)
        val message = email.render().bytes
        maxUploadSize(account.session)?.let { max ->
            if (message.size > max) {
                throw JmapException("Email has ${message.size} bytes, but the server accepts at most $max")
            }
        }

        val blobId = upload(account, message)
        val mailbox = account.drafts ?: account.sent
            ?: throw JmapException("Account has neither a Drafts nor a Sent mailbox")
        val emailId = imported(account, blobId, mailbox)

        val submission = call(
            account,
            "EmailSubmission/set",
            buildJsonObject {
                put("accountId", account.accountId)
                putJsonObject("create") {
                    putJsonObject("s") {
                        put("identityId", identity)
                        put("emailId", emailId)
                        putJsonObject("envelope") {
                            putJsonObject("mailFrom") { put("email", email.from.address) }
                            putJsonArray("rcptTo") {
                                email.recipients.forEach { add(buildJsonObject { put("email", it.address) }) }
                            }
                        }
                    }
                }
                putJsonObject("onSuccessUpdateEmail") {
                    putJsonObject("#s") {
                        put("keywords/\$draft", JsonNull)
                        if (account.sent != null && account.sent != mailbox) {
                            put("mailboxIds/$mailbox", JsonNull)
                            put("mailboxIds/${account.sent}", true)
                        }
                    }
                }
            },
        )
        val submissionId = created(submission, "s", "EmailSubmission/set")
        return SendReceipt(email.messageId, email.recipients, serverResponse = submissionId)
    }

    override suspend fun close() {
        if (ownsClient) client.close()
    }

    private suspend fun account(): Account = lock.withLock {
        account ?: loadAccount().also { account = it }
    }

    private suspend fun loadAccount(): Account {
        val response = client.get(config.sessionUrl) { authorize() }
        val session = decode(Session.serializer(), checked(response, "session").bodyAsText())
        if (Capability.SUBMISSION !in session.capabilities) {
            throw JmapException("Server does not support email submission")
        }
        val accountId = session.primaryAccounts[Capability.SUBMISSION] ?: session.primaryAccounts[Capability.MAIL]
            ?: throw JmapException("Server has no primary account for email submission")

        val apiUrl = resolve(session.apiUrl)
        val responses = request(
            apiUrl,
            listOf(
                invocation("Mailbox/query", roleFilter(accountId, "drafts"), "drafts"),
                invocation("Mailbox/query", roleFilter(accountId, "sent"), "sent"),
                invocation("Identity/get", buildJsonObject { put("accountId", accountId) }, "identities"),
            ),
        )
        val drafts = decode(QueryResponse.serializer(), responses.single("drafts", "Mailbox/query")).ids.firstOrNull()
        val sent = decode(QueryResponse.serializer(), responses.single("sent", "Mailbox/query")).ids.firstOrNull()
        val identities = decode(
            GetResponse.serializer(Identity.serializer()),
            responses.single("identities", "Identity/get"),
        ).list
        return Account(session, apiUrl, resolve(session.uploadUrl), accountId, drafts, sent, identities)
    }

    private fun identityFor(account: Account, email: Email): String {
        config.identityId?.let { return it }
        val from = email.from.address
        val identity = account.identities.firstOrNull { it.email.equals(from, ignoreCase = true) }
            ?: account.identities.firstOrNull { it.email.equals("*@${email.from.domain}", ignoreCase = true) }
        return identity?.id ?: throw JmapException(
            "No identity for $from, available: ${account.identities.map { it.email }}",
            type = "forbiddenFrom",
        )
    }

    private suspend fun upload(account: Account, message: ByteArray): String {
        val url = account.uploadUrl.replace("{accountId}", account.accountId)
        val response = client.post(url) {
            authorize()
            setBody(ByteArrayContent(message, ContentType("message", "rfc822")))
        }
        return decode(UploadResponse.serializer(), checked(response, "upload").bodyAsText()).blobId
    }

    private suspend fun imported(account: Account, blobId: String, mailbox: String): String {
        val response = call(
            account,
            "Email/import",
            buildJsonObject {
                put("accountId", account.accountId)
                putJsonObject("emails") {
                    putJsonObject("m") {
                        put("blobId", blobId)
                        putJsonObject("mailboxIds") { put(mailbox, true) }
                        putJsonObject("keywords") {
                            put("\$draft", true)
                            put("\$seen", true)
                        }
                    }
                }
            },
        )
        return created(response, "m", "Email/import")
    }

    private fun created(response: JsonObject, creationId: String, method: String): String {
        val result = decode(CreateResponse.serializer(), response)
        result.created?.get(creationId)?.let { return it.id }
        val error = result.notCreated?.get(creationId)
        throw JmapException("$method failed: ${error?.type} ${error?.description.orEmpty()}".trim(), error?.type)
    }

    private suspend fun call(account: Account, method: String, arguments: JsonObject): JsonObject =
        request(account.apiUrl, listOf(invocation(method, arguments, "c"))).single("c", method)

    private suspend fun request(apiUrl: String, calls: List<JsonArray>): List<MethodResponse> {
        val body = json.encodeToString(Request.serializer(), Request(USING, calls))
        val response = client.post(apiUrl) {
            authorize()
            setBody(TextContent(body, ContentType.Application.Json))
        }
        val parsed = decode(Response.serializer(), checked(response, "API request").bodyAsText())
        return parsed.methodResponses.map {
            MethodResponse(it[0].jsonPrimitive.content, it[1].jsonObject, it[2].jsonPrimitive.content)
        }
    }

    private fun List<MethodResponse>.single(callId: String, method: String): JsonObject {
        val response = firstOrNull { it.callId == callId } ?: throw JmapException("No response for $method")
        if (response.name == "error") {
            val type = response.arguments["type"]?.jsonPrimitive?.content
            val description = response.arguments["description"]?.jsonPrimitive?.content.orEmpty()
            throw JmapException("$method failed: $type $description".trim(), type)
        }
        return response.arguments
    }

    private suspend fun checked(response: HttpResponse, action: String): HttpResponse {
        if (response.status.isSuccess()) return response
        // RFC 8620 3.6.1: request level errors are RFC 7807 problem details
        val body = response.bodyAsText()
        val problem = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val type = (problem?.get("type") as? JsonPrimitive)?.content
        val detail = (problem?.get("detail") as? JsonPrimitive)?.content ?: body.take(MAX_ERROR_BODY)
        val status = response.status.value
        throw JmapException("JMAP $action failed with HTTP $status: $detail", type, status)
    }

    private suspend fun HttpRequestBuilder.authorize() {
        val value = when (val auth = config.auth) {
            is JmapAuth.Bearer -> "Bearer ${auth.token()}"
            is JmapAuth.Basic -> "Basic " + Base64.encode("${auth.username}:${auth.password}".encodeToByteArray())
        }
        header(HttpHeaders.Authorization, value)
    }

    /** Session URLs may be relative to the session resource. */
    private fun resolve(url: String): String = if (url.startsWith("https://") || url.startsWith("http://")) {
        url
    } else {
        // keep URL template variables like {accountId} unencoded
        URLBuilder(config.sessionUrl).takeFrom(url).buildString().replace("%7B", "{").replace("%7D", "}")
    }

    private fun maxUploadSize(session: Session): Long? =
        (session.capabilities[Capability.CORE] as? JsonObject)?.get("maxSizeUpload")
            ?.jsonPrimitive?.content?.toLongOrNull()

    private fun <T> decode(deserializer: DeserializationStrategy<T>, text: String): T = try {
        json.decodeFromString(deserializer, text)
    } catch (e: SerializationException) {
        throw JmapException("Unexpected JMAP response: ${e.message}", cause = e)
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, element: JsonObject): T = try {
        json.decodeFromJsonElement(deserializer, element)
    } catch (e: SerializationException) {
        throw JmapException("Unexpected JMAP response: ${e.message}", cause = e)
    }

    private companion object {
        const val MAX_ERROR_BODY = 500
        val USING = listOf(Capability.CORE, Capability.MAIL, Capability.SUBMISSION)
        val json = Json { ignoreUnknownKeys = true }

        fun invocation(method: String, arguments: JsonObject, callId: String) = buildJsonArray {
            add(JsonPrimitive(method))
            add(arguments)
            add(JsonPrimitive(callId))
        }

        fun roleFilter(accountId: String, role: String) = buildJsonObject {
            put("accountId", accountId)
            putJsonObject("filter") { put("role", role) }
        }
    }
}
