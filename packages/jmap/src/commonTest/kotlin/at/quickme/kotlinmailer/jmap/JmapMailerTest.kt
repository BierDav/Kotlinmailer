package at.quickme.kotlinmailer.jmap

import at.quickme.kotlinmailer.email.emailBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JmapMailerTest {
    private class FakeJmapServer {
        var capabilities = listOf(Capability.CORE, Capability.MAIL, Capability.SUBMISSION)
        var identities = listOf("id-alice" to "alice@example.com", "id-wild" to "*@example.org")
        var sentMailbox: String? = "mb-sent"
        var methodOverrides = mutableMapOf<String, JsonArray>()
        var maxSizeUpload = 50_000_000

        val sessionRequests = mutableListOf<HttpRequestData>()
        val uploads = mutableListOf<String>()
        val calls = mutableListOf<JsonArray>()
        val authorizations = mutableListOf<String?>()

        val client = HttpClient(
            MockEngine { request ->
                authorizations += request.headers[HttpHeaders.Authorization]
                val url = request.url.toString()
                when {
                    request.method == HttpMethod.Get && url == SESSION_URL -> {
                        sessionRequests += request
                        json(session())
                    }

                    url.startsWith("https://jmap.example.com/upload/acc1/") -> {
                        uploads += body(request)
                        json(buildJsonObject {
                            put("accountId", "acc1")
                            put("blobId", "blob1")
                            put("type", "message/rfc822")
                            put("size", 123)
                        })
                    }

                    url == "https://jmap.example.com/api/" -> api(request)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        )

        private fun session() = buildJsonObject {
            putJsonObject("capabilities") {
                capabilities.forEach { capability ->
                    putJsonObject(capability) { if (capability == Capability.CORE) put("maxSizeUpload", maxSizeUpload) }
                }
            }
            putJsonObject("accounts") { putJsonObject("acc1") { put("name", "alice@example.com") } }
            putJsonObject("primaryAccounts") {
                put(Capability.MAIL, "acc1")
                if (Capability.SUBMISSION in capabilities) put(Capability.SUBMISSION, "acc1")
            }
            put("username", "alice@example.com")
            // relative URL, resolved against the session URL
            put("apiUrl", "/api/")
            put("uploadUrl", "https://jmap.example.com/upload/{accountId}/")
            put("downloadUrl", "https://jmap.example.com/download/{accountId}/{blobId}/{name}")
            put("state", "s1")
        }

        private fun MockRequestHandleScope.api(request: HttpRequestData): HttpResponseData {
            val methodCalls = Json.parseToJsonElement(body(request)).jsonObject["methodCalls"]!!.jsonArray
            val responses = methodCalls.map { it.jsonArray }.map { call ->
                calls += call
                val name = call[0].jsonPrimitive.content
                val args = call[1].jsonObject
                val callId = call[2]
                methodOverrides[name]?.let { return@map JsonArray(listOf(it[0], it[1], callId)) }
                val result: JsonElement = when (name) {
                    "Mailbox/query" -> buildJsonObject {
                        val role = args["filter"]!!.jsonObject["role"]!!.jsonPrimitive.content
                        putJsonArray("ids") {
                            if (role == "drafts") add(JsonPrimitive("mb-drafts"))
                            if (role == "sent") sentMailbox?.let { add(JsonPrimitive(it)) }
                        }
                    }

                    "Identity/get" -> buildJsonObject {
                        putJsonArray("list") {
                            identities.forEach { (id, email) ->
                                add(buildJsonObject { put("id", id); put("email", email) })
                            }
                        }
                    }

                    "Email/import" -> buildJsonObject {
                        putJsonObject("created") { putJsonObject("m") { put("id", "email1") } }
                    }
                    "EmailSubmission/set" -> buildJsonObject {
                        putJsonObject("created") { putJsonObject("s") { put("id", "sub1") } }
                    }

                    else -> buildJsonObject { put("type", "unknownMethod") }
                }
                buildJsonArray {
                    add(JsonPrimitive(if (result.jsonObject["type"] != null) "error" else name))
                    add(result)
                    add(callId)
                }
            }
            return json(buildJsonObject { put("methodResponses", JsonArray(responses)); put("sessionState", "s1") })
        }

        fun call(name: String) = calls.last { it[0].jsonPrimitive.content == name }[1].jsonObject

        private fun body(request: HttpRequestData) =
            (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

        private fun MockRequestHandleScope.json(value: JsonObject) =
            respond(value.toString(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private val email = emailBuilder {
        from("Alice <alice@example.com>")
        to("bob@example.com")
        bcc("carol@example.com")
        subject = "Hello via JMAP"
        text = "Hi Bob"
    }

    private fun mailer(server: FakeJmapServer, block: JmapMailerBuilder.() -> Unit = {}) = JmapMailer(
        JmapMailerBuilder(SESSION_URL).apply {
            bearer("token")
            httpClient = server.client
            block()
        }.build(),
    )

    @Test
    fun uploadsImportsAndSubmitsEmail() = runTest {
        val server = FakeJmapServer()
        val receipt = mailer(server).send(email)

        assertEquals("sub1", receipt.serverResponse)
        assertEquals(email.messageId, receipt.messageId)
        assertTrue(server.authorizations.all { it == "Bearer token" })

        val uploaded = server.uploads.single()
        assertContains(uploaded, "Subject: Hello via JMAP\r\n")
        assertTrue("carol@example.com" !in uploaded)

        val import = server.call("Email/import")["emails"]!!.jsonObject["m"]!!.jsonObject
        assertEquals("blob1", import["blobId"]!!.jsonPrimitive.content)
        assertEquals(setOf("mb-drafts"), import["mailboxIds"]!!.jsonObject.keys)
        assertEquals(setOf("\$draft", "\$seen"), import["keywords"]!!.jsonObject.keys)

        val submission = server.call("EmailSubmission/set")
        val created = submission["create"]!!.jsonObject["s"]!!.jsonObject
        assertEquals("id-alice", created["identityId"]!!.jsonPrimitive.content)
        assertEquals("email1", created["emailId"]!!.jsonPrimitive.content)
        val envelope = created["envelope"]!!.jsonObject
        assertEquals("alice@example.com", envelope["mailFrom"]!!.jsonObject["email"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("bob@example.com", "carol@example.com"),
            envelope["rcptTo"]!!.jsonArray.map { it.jsonObject["email"]!!.jsonPrimitive.content },
        )
        val update = submission["onSuccessUpdateEmail"]!!.jsonObject["#s"]!!.jsonObject
        assertEquals(JsonNull, update["mailboxIds/mb-drafts"])
        assertEquals(JsonPrimitive(true), update["mailboxIds/mb-sent"])
        assertEquals(JsonNull, update["keywords/\$draft"])
    }

    @Test
    fun cachesSessionAndMailboxes() = runTest {
        val server = FakeJmapServer()
        val mailer = mailer(server)
        mailer.send(email)
        mailer.send(email)
        assertEquals(1, server.sessionRequests.size)
        assertEquals(1, server.calls.count { it[0].jsonPrimitive.content == "Identity/get" })
        assertEquals(2, server.uploads.size)
    }

    @Test
    fun usesBasicAuth() = runTest {
        val server = FakeJmapServer()
        mailer(server) { basic("alice", "pw") }.send(email)
        assertEquals("Basic YWxpY2U6cHc=", server.authorizations.first())
    }

    @Test
    fun matchesWildcardIdentities() = runTest {
        val server = FakeJmapServer()
        mailer(server).send(email.newBuilder().apply { from("info@example.org") }.build())
        val submission = server.call("EmailSubmission/set")["create"]!!.jsonObject["s"]!!.jsonObject
        assertEquals("id-wild", submission["identityId"]!!.jsonPrimitive.content)
    }

    @Test
    fun failsWithoutMatchingIdentity() = runTest {
        val server = FakeJmapServer()
        val error = assertFailsWith<JmapException> {
            mailer(server).send(email.newBuilder().apply { from("mallory@example.com") }.build())
        }
        assertEquals("forbiddenFrom", error.type)
        assertTrue(server.uploads.isEmpty())
    }

    @Test
    fun keepsEmailInDraftsWithoutSentMailbox() = runTest {
        val server = FakeJmapServer().apply { sentMailbox = null }
        mailer(server).send(email)
        val update = server.call("EmailSubmission/set")["onSuccessUpdateEmail"]!!.jsonObject["#s"]!!.jsonObject
        assertEquals(setOf("keywords/\$draft"), update.keys)
    }

    @Test
    fun reportsSetErrors() = runTest {
        val server = FakeJmapServer()
        server.methodOverrides["EmailSubmission/set"] = buildJsonArray {
            add(JsonPrimitive("EmailSubmission/set"))
            add(buildJsonObject {
                putJsonObject("notCreated") {
                    putJsonObject("s") {
                        put("type", "forbiddenToSend")
                        put("description", "Sending limit reached")
                    }
                }
            })
        }
        val error = assertFailsWith<JmapException> { mailer(server).send(email) }
        assertEquals("forbiddenToSend", error.type)
        assertContains(error.message!!, "Sending limit reached")
    }

    @Test
    fun reportsMethodErrors() = runTest {
        val server = FakeJmapServer()
        server.methodOverrides["Email/import"] = buildJsonArray {
            add(JsonPrimitive("error"))
            add(buildJsonObject { put("type", "overQuota") })
        }
        assertEquals("overQuota", assertFailsWith<JmapException> { mailer(server).send(email) }.type)
    }

    @Test
    fun reportsRequestLevelProblems() = runTest {
        val client = HttpClient(
            MockEngine {
                respond(
                    """{"type":"about:blank","status":401,"detail":"Invalid token"}""",
                    HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.ContentType, "application/problem+json"),
                )
            },
        )
        val error = assertFailsWith<JmapException> { mailer(FakeJmapServer()) { httpClient = client }.send(email) }
        assertEquals(401, error.httpStatus)
        assertContains(error.message!!, "Invalid token")
    }

    @Test
    fun requiresSubmissionCapability() = runTest {
        val server = FakeJmapServer().apply { capabilities = listOf(Capability.CORE, Capability.MAIL) }
        assertContains(assertFailsWith<JmapException> { mailer(server).send(email) }.message!!, "submission")
    }

    @Test
    fun rejectsEmailsLargerThanUploadLimit() = runTest {
        val server = FakeJmapServer().apply { maxSizeUpload = 10 }
        assertContains(assertFailsWith<JmapException> { mailer(server).send(email) }.message!!, "at most 10")
        assertTrue(server.uploads.isEmpty())
    }

    @Test
    fun buildsWellKnownSessionUrl() {
        assertEquals("https://example.com/.well-known/jmap", JmapConfig.wellKnownSessionUrl("example.com"))
    }

    private companion object {
        const val SESSION_URL = "https://jmap.example.com/session"
    }
}
