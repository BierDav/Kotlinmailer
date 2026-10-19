package at.quickme.kotlinmailer.jmap

import at.quickme.kotlinmailer.delivery.MailException

/**
 * A JMAP failure. [type] is the JMAP error type, e.g. `forbiddenFrom` or `urn:ietf:params:jmap:error:limit`.
 */
public class JmapException(
    message: String,
    public val type: String? = null,
    /** The HTTP status code, if the request itself failed. */
    public val httpStatus: Int? = null,
    cause: Throwable? = null,
) : MailException(message, cause)
