
package at.quickme.kotlinmailer.delivery

import at.quickme.kotlinmailer.data.SMTPLoginInfo
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.mailer.MailerBuilder
import org.simplejavamail.mailer.internal.MailerRegularBuilderImpl
import kotlin.contracts.contract

/**
 * Open a mailer builder.
 * This function automatically builds the email and
 * returns it.
 */
inline fun mailerBuilder(smtpLoginInfo: SMTPLoginInfo, builder: MailerRegularBuilderImpl.() -> Unit = {}): Mailer {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return mailerBuilder(
        smtpLoginInfo.host,
        smtpLoginInfo.port,
        smtpLoginInfo.username,
        smtpLoginInfo.password,
        builder
    )
}

/**
 * Open a mailer builder.
 * This function automatically builds the email and
 * returns it.
 */
inline fun mailerBuilder(
    host: String = "localhost",
    port: Int = 25,
    username: String? = null,
    password: String? = null,
    builder: MailerRegularBuilderImpl.() -> Unit = {}
): Mailer {
    contract {
        callsInPlace(builder, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return MailerBuilder
        .withSMTPServer(host, port, username, password)
        .apply(builder)
        .buildMailer().apply {
            MailerManager.registerMailer(this)
        }
}
