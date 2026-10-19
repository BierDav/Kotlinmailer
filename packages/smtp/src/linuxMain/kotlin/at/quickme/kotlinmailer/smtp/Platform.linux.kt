package at.quickme.kotlinmailer.smtp

// ktor-network-tls has no TLS implementation for Kotlin/Native yet
internal actual val ktorTlsSupported: Boolean = false

public actual fun defaultSmtpSocketFactory(): SmtpSocketFactory = KtorSmtpSocketFactory()
