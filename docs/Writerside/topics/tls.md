# TLS

TLS ([Transport Layer Security](https://en.wikipedia.org/wiki/Transport_Layer_Security)) encrypts the connection to
your SMTP server. Kotlinmailer verifies the server's certificate using the platform's trust store.

## Security modes

```kotlin
smtpMailer("smtp.example.com") {
    // implicit TLS (SMTPS, RFC 8314), port 465
    security = SmtpSecurity.TLS
    // or upgrade with STARTTLS (RFC 3207), port 587; this is the default
    security = SmtpSecurity.STARTTLS
}
```

`SmtpSecurity.STARTTLS` fails if the server does not offer STARTTLS. `STARTTLS_IF_AVAILABLE` and `NONE` may send
emails unencrypted and should only be used in trusted networks, e.g. for a relay on `localhost`. Credentials are
never sent over an unencrypted connection unless you set `allowInsecureAuth = true`.

## Platforms

| Platform              | TLS implementation                       |
|-----------------------|------------------------------------------|
| JVM / Android         | Ktor (`ktor-network-tls`)                |
| macOS, iOS            | Foundation streams (system TLS)          |
| Node.js               | Node's `tls` module                      |
| Linux, Windows native | not available, only `SmtpSecurity.NONE`  |

## Custom certificates

On the JVM you can configure the TLS handshake, e.g. to trust the self-signed certificate of a test server:

```kotlin
smtpMailer("localhost") {
    socketFactory = KtorSmtpSocketFactory {
        trustManager = myTrustManager
    }
}
```

On Node.js, pass options to `tls.connect`, e.g. `NodeSmtpSocketFactory(js("({ ca: myCa })"))`.

To run SMTP over a completely custom transport, implement `SmtpSocket` and `SmtpSocketFactory`.

<tip>
Testing locally? Run a fake SMTP server such as <a href="https://greenmail-mail-test.github.io/greenmail/">GreenMail</a>
or <a href="https://github.com/axllent/mailpit">Mailpit</a> with <code>SmtpSecurity.NONE</code>.
</tip>
