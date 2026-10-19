package at.quickme.kotlinmailer.jmap

import at.quickme.kotlinmailer.delivery.MailerManager
import io.ktor.client.HttpClient
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Authentication for JMAP requests. */
public sealed interface JmapAuth {
    /** `Authorization: Bearer <token>`, e.g. an API token or OAuth 2.0 access token. */
    public class Bearer(public val token: suspend () -> String) : JmapAuth {
        public constructor(token: String) : this({ token })

        override fun toString(): String = "Bearer(***)"
    }

    /** `Authorization: Basic`, with username and (app) password. */
    public class Basic(public val username: String, public val password: String) : JmapAuth {
        override fun toString(): String = "Basic(username=$username)"
    }
}

/** Settings of a [JmapMailer]. */
public class JmapConfig(
    /** The JMAP session resource, e.g. `https://api.fastmail.com/jmap/session`. */
    public val sessionUrl: String,
    public val auth: JmapAuth,
    /** The client used for all requests; one with the default engine is created (and closed) if `null`. */
    public val httpClient: HttpClient? = null,
    /** The identity to send with; by default the identity matching the email's `From` address. */
    public val identityId: String? = null,
) {
    public companion object {
        /** The well-known session URL of [host], see RFC 8620 section 2.2. */
        public fun wellKnownSessionUrl(host: String): String = "https://$host/.well-known/jmap"
    }
}

/** Builder for [JmapMailer], see [jmapMailer]. */
public class JmapMailerBuilder(public var sessionUrl: String) {
    public var auth: JmapAuth? = null
    public var httpClient: HttpClient? = null
    public var identityId: String? = null

    public fun bearer(token: String) {
        auth = JmapAuth.Bearer(token)
    }

    public fun bearer(token: suspend () -> String) {
        auth = JmapAuth.Bearer(token)
    }

    public fun basic(username: String, password: String) {
        auth = JmapAuth.Basic(username, password)
    }

    public fun build(): JmapConfig =
        JmapConfig(sessionUrl, checkNotNull(auth) { "JMAP requires authentication" }, httpClient, identityId)
}

/**
 * Create a [JmapMailer] and register it at [MailerManager].
 *
 * @param sessionUrl the JMAP session resource, see [JmapConfig.wellKnownSessionUrl]
 */
public inline fun jmapMailer(sessionUrl: String, builder: JmapMailerBuilder.() -> Unit): JmapMailer {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    return JmapMailer(JmapMailerBuilder(sessionUrl).apply(builder).build()).also { MailerManager.registerMailer(it) }
}
