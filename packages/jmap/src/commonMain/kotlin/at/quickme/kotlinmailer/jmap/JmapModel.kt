package at.quickme.kotlinmailer.jmap

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal object Capability {
    const val CORE = "urn:ietf:params:jmap:core"
    const val MAIL = "urn:ietf:params:jmap:mail"
    const val SUBMISSION = "urn:ietf:params:jmap:submission"
}

/** RFC 8620 section 2. */
@Serializable
internal data class Session(
    val capabilities: Map<String, JsonElement>,
    val primaryAccounts: Map<String, String>,
    val apiUrl: String,
    val uploadUrl: String,
)

/** RFC 8620 section 3.3 and 3.4; invocations are `[name, arguments, callId]`. */
@Serializable
internal data class Request(val using: List<String>, val methodCalls: List<JsonArray>)

@Serializable
internal data class Response(val methodResponses: List<JsonArray>)

/** RFC 8620 section 6.1. */
@Serializable
internal data class UploadResponse(val blobId: String, val size: Long)

/** RFC 8621 section 6. */
@Serializable
internal data class Identity(val id: String, val email: String)

/** RFC 8620 section 5.3. */
@Serializable
internal data class SetError(val type: String, val description: String? = null)

@Serializable
internal data class Created(val id: String)

/** The relevant parts of `Email/import` and `EmailSubmission/set` responses. */
@Serializable
internal data class CreateResponse(
    val created: Map<String, Created>? = null,
    val notCreated: Map<String, SetError>? = null,
)

@Serializable
internal data class QueryResponse(val ids: List<String>)

@Serializable
internal data class GetResponse<T>(val list: List<T>)

internal class MethodResponse(val name: String, val arguments: JsonObject, val callId: String)
