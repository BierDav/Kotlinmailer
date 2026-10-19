package at.quickme.kotlinmailer.delivery

import at.quickme.kotlinmailer.email.Email
import at.quickme.kotlinmailer.email.EmailAddress

/**
 * Delivers emails, e.g. via SMTP or JMAP.
 */
public interface Mailer {
    /**
     * Send [email] and suspend until the server accepted it.
     *
     * @throws MailException if the email could not be delivered
     */
    public suspend fun send(email: Email): SendReceipt

    /** Release all resources, such as open connections. */
    public suspend fun close()
}

/** The result of a successful [Mailer.send]. */
public class SendReceipt(
    /** The `Message-ID` of the sent email. */
    public val messageId: String,
    /** Recipients the server accepted. */
    public val acceptedRecipients: List<EmailAddress>,
    /** Recipients the server rejected, with the server's reason. */
    public val rejectedRecipients: Map<EmailAddress, String> = emptyMap(),
    /** The final server response, e.g. the SMTP queue id or the JMAP submission id. */
    public val serverResponse: String? = null,
) {
    override fun toString(): String =
        "SendReceipt(messageId=$messageId, accepted=$acceptedRecipients, rejected=$rejectedRecipients)"
}

/** Thrown if an email could not be delivered. */
public open class MailException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Send this email.
 *
 * @param mailer the mailer used for delivery, [MailerManager.defaultMailer] by default
 */
public suspend fun Email.send(mailer: Mailer = MailerManager.defaultMailer): SendReceipt = mailer.send(this)
