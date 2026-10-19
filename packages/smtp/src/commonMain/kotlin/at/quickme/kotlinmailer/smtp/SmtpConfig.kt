package at.quickme.kotlinmailer.smtp

import at.quickme.kotlinmailer.delivery.MailerManager
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How the connection to the SMTP server is secured. */
public enum class SmtpSecurity(internal val defaultPort: Int) {
    /** Implicit TLS from the first byte (SMTPS, RFC 8314), usually port 465. */
    TLS(465),

    /** Upgrade via STARTTLS (RFC 3207); fails if the server doesn't support it. Usually port 587. */
    STARTTLS(587),

    /** Use STARTTLS if the server offers it, otherwise stay unencrypted. Only for trusted networks. */
    STARTTLS_IF_AVAILABLE(25),

    /** No encryption. Only for trusted networks, e.g. a local relay. */
    NONE(25),
}

/** Credentials for SMTP AUTH (RFC 4954). */
public sealed interface SmtpAuth {
    public val username: String

    /** Authenticate with `PLAIN` or `LOGIN`. */
    public class Password(override val username: String, public val password: String) : SmtpAuth {
        override fun toString(): String = "Password(username=$username)"
    }

    /** Authenticate with an OAuth 2.0 access token using `OAUTHBEARER` (RFC 7628) or `XOAUTH2`. */
    public class OAuth2(override val username: String, public val accessToken: suspend () -> String) : SmtpAuth {
        override fun toString(): String = "OAuth2(username=$username)"
    }
}

/** Settings of an [SmtpMailer]. */
public class SmtpConfig(
    public val host: String,
    public val port: Int = SmtpSecurity.STARTTLS.defaultPort,
    public val security: SmtpSecurity = SmtpSecurity.STARTTLS,
    public val auth: SmtpAuth? = null,
    /** The client's domain name or address literal sent with `EHLO`. */
    public val clientHostname: String = "localhost",
    public val connectTimeout: Duration = 30.seconds,
    /** Timeout for each command reply; RFC 5321 suggests 5 minutes. */
    public val commandTimeout: Duration = 5.minutes,
    /** Timeout for the reply after the message data; RFC 5321 suggests 10 minutes. */
    public val dataTimeout: Duration = 10.minutes,
    /** Maximum number of parallel connections. */
    public val maxConnections: Int = 1,
    /** Keep connections open between emails. */
    public val reuseConnections: Boolean = true,
    /** Fail the whole email if any recipient is rejected, otherwise deliver to the accepted ones. */
    public val failOnRejectedRecipient: Boolean = true,
    /** Allow sending credentials over an unencrypted connection. */
    public val allowInsecureAuth: Boolean = false,
    public val socketFactory: SmtpSocketFactory = defaultSmtpSocketFactory(),
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..MAX_PORT) { "Invalid port $port" }
        require(maxConnections > 0) { "maxConnections must be positive" }
        require(clientHostname.isNotBlank() && clientHostname.none { it.isWhitespace() }) { "Invalid clientHostname" }
    }

    private companion object {
        const val MAX_PORT = 65535
    }
}

/** Builder for [SmtpMailer], see [smtpMailer]. */
public class SmtpMailerBuilder(public var host: String) {
    public var security: SmtpSecurity = SmtpSecurity.STARTTLS

    /** Defaults to the usual port for [security]. */
    public var port: Int? = null
    public var auth: SmtpAuth? = null
    public var clientHostname: String = "localhost"
    public var connectTimeout: Duration = 30.seconds
    public var commandTimeout: Duration = 5.minutes
    public var dataTimeout: Duration = 10.minutes
    public var maxConnections: Int = 1
    public var reuseConnections: Boolean = true
    public var failOnRejectedRecipient: Boolean = true
    public var allowInsecureAuth: Boolean = false
    public var socketFactory: SmtpSocketFactory = defaultSmtpSocketFactory()

    /** Authenticate with username and password. */
    public fun credentials(username: String, password: String) {
        auth = SmtpAuth.Password(username, password)
    }

    /** Authenticate with an OAuth 2.0 access token, fetched for every new connection. */
    public fun oauth2(username: String, accessToken: suspend () -> String) {
        auth = SmtpAuth.OAuth2(username, accessToken)
    }

    public fun build(): SmtpConfig = SmtpConfig(
        host, port ?: security.defaultPort, security, auth, clientHostname, connectTimeout, commandTimeout,
        dataTimeout, maxConnections, reuseConnections, failOnRejectedRecipient, allowInsecureAuth, socketFactory,
    )
}

/**
 * Create an [SmtpMailer] and register it at [MailerManager].
 *
 * No connection is opened until the first email is sent.
 */
public inline fun smtpMailer(host: String, builder: SmtpMailerBuilder.() -> Unit = {}): SmtpMailer {
    contract { callsInPlace(builder, InvocationKind.EXACTLY_ONCE) }
    return SmtpMailer(SmtpMailerBuilder(host).apply(builder).build()).also { MailerManager.registerMailer(it) }
}
