# Mailer API

A `Mailer` delivers emails. Kotlinmailer ships two implementations:

| Mailer       | Module | Protocol           | Platforms                                                    |
|--------------|--------|--------------------|--------------------------------------------------------------|
| `SmtpMailer` | `smtp` | SMTP (RFC 5321)    | JVM / Android, Node.js, macOS, iOS, Linux & Windows (no TLS)  |
| `JmapMailer` | `jmap` | JMAP (RFC 8620/21) | every Kotlin target with a Ktor client engine, incl. browsers |

Both implement the same interface, so the rest of your code does not care which one you use.

## SMTP

```kotlin
val mailer = smtpMailer("smtp.example.com") {
    security = SmtpSecurity.STARTTLS // the default, port 587
    credentials("john.doe@example.com", "app-password")
}
```

| Option                    | Default             | Description                                                                         |
|---------------------------|---------------------|-------------------------------------------------------------------------------------|
| `security`                | `STARTTLS`          | `TLS` (port 465), `STARTTLS` (587), `STARTTLS_IF_AVAILABLE` or `NONE` (25)          |
| `port`                    | depends on security | The server port                                                                     |
| `credentials(user, pass)` | none                | Authenticate with `PLAIN` or `LOGIN`                                                |
| `oauth2(user) { token }`  | none                | Authenticate with `OAUTHBEARER` or `XOAUTH2`, the token is fetched per connection   |
| `clientHostname`          | `localhost`         | The name sent with `EHLO`                                                           |
| `maxConnections`          | `1`                 | Number of parallel connections                                                      |
| `reuseConnections`        | `true`              | Keep connections open between emails                                                |
| `failOnRejectedRecipient` | `true`              | Fail if any recipient is rejected, otherwise deliver to the accepted ones           |
| `allowInsecureAuth`       | `false`             | Allow sending credentials without TLS                                               |
| `connectTimeout`, `commandTimeout`, `dataTimeout` | 30 s, 5 min, 10 min | Timeouts as recommended by RFC 5321                             |

The client supports the ESMTP extensions `STARTTLS`, `AUTH`, `PIPELINING`, `SIZE`, `8BITMIME`, `SMTPUTF8` and
`ENHANCEDSTATUSCODES`. No connection is opened until the first email is sent; call `mailer.testConnection()` to check
your configuration eagerly.

## JMAP

JMAP is a modern HTTP based protocol supported by e.g. Fastmail and Stalwart. As it only needs HTTP, it also works in
browsers.

```kotlin
val mailer = jmapMailer("https://api.fastmail.com/jmap/session") {
    bearer("api-token")
}
```

If you only know the host, use `JmapConfig.wellKnownSessionUrl("example.com")`.

The email is uploaded, imported into the Drafts mailbox and submitted, after which the server moves it to the Sent
mailbox. The identity matching the `From` address is used; set `identityId` to choose a specific one.

<note>
The <code>jmap</code> module only depends on <code>ktor-client-core</code>. Add a
<a href="https://ktor.io/docs/client-engines.html">Ktor client engine</a> such as <code>ktor-client-cio</code>,
<code>ktor-client-darwin</code> or <code>ktor-client-js</code> to your project, or pass your own
<code>httpClient</code>.
</note>

## Send emails

`send` suspends until the server accepted the email and returns a `SendReceipt`.

```kotlin
val receipt = email.send(mailer)
println("Sent ${receipt.messageId} to ${receipt.acceptedRecipients}")
```

Failures are thrown as `MailException`; for SMTP this is an `SmtpException` with the server's `replyCode`,
`enhancedStatusCode` and `isTransient`, for JMAP a `JmapException` with the JMAP error `type`.

```kotlin
try {
    email.send(mailer)
} catch (e: SmtpException) {
    if (e.isTransient) retryLater(email)
}
```

## Default mailer

The default mailer is used if `send()` is called without a mailer.

```kotlin
MailerManager.defaultMailer = mailer
email.send()
```

## Shut down

Close mailers to release their connections. `smtpMailer` and `jmapMailer` register their mailers at the
`MailerManager`, so you can close all of them at once:

```kotlin
MailerManager.shutdownMailers()
```
