package at.quickme.kotlinmailer.smtp

import at.quickme.kotlinmailer.delivery.MailException

/**
 * An SMTP failure. If the server rejected a command, [replyCode] and [enhancedStatusCode] are set.
 */
public class SmtpException(
    message: String,
    /** The three digit reply code, e.g. `550`. */
    public val replyCode: Int? = null,
    /** The RFC 3463 enhanced status code, e.g. `5.1.1`. */
    public val enhancedStatusCode: String? = null,
    cause: Throwable? = null,
) : MailException(message, cause) {
    /** True for 4xx replies: retrying later may succeed. */
    public val isTransient: Boolean get() = replyCode in ReplyCode.FIRST_TRANSIENT until ReplyCode.FIRST_PERMANENT

    internal companion object {
        fun from(command: String, reply: SmtpReply) = SmtpException(
            "SMTP $command failed: ${reply.code} ${reply.message}",
            reply.code,
            reply.enhancedStatusCode,
        )
    }
}
