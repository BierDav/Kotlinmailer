@file:JvmName("SmtpPlatform")

package at.quickme.kotlinmailer.smtp

internal actual val ktorTlsSupported: Boolean = true

public actual fun defaultSmtpSocketFactory(): SmtpSocketFactory = KtorSmtpSocketFactory()
