# Migration Guide

## From 1.x to 2.0

Kotlinmailer 2.0 is a Kotlin Multiplatform library with its own SMTP and JMAP clients. It no longer depends on
SimpleJavaMail or Jakarta Mail, so the SimpleJavaMail types are gone from the API.

### Dependencies

Add the `smtp` (or `jmap`) module next to `core`:

```kotlin
implementation("at.quickme.kotlinmailer:core:%kotlinmailer_version%")
implementation("at.quickme.kotlinmailer:smtp:%kotlinmailer_version%")
```

### Mailer

| 1.x                                                | 2.0                                                              |
|----------------------------------------------------|------------------------------------------------------------------|
| `mailerBuilder(host, port, username, password)`    | `smtpMailer(host) { this.port = port; credentials(username, password) }` |
| `withTransportStrategy(TransportStrategy.SMTP_TLS)` | `security = SmtpSecurity.STARTTLS`                              |
| `withTransportStrategy(TransportStrategy.SMTPS)`   | `security = SmtpSecurity.TLS`                                    |
| `withTransportStrategy(TransportStrategy.SMTP)`    | `security = SmtpSecurity.NONE`                                   |
| `SMTPLoginInfo`                                    | `SmtpConfig`                                                     |

<warning>
In 1.x, <code>mailerBuilder</code> connected without encryption unless a transport strategy was set. 2.0 defaults to
<code>SmtpSecurity.STARTTLS</code> on port 587 and refuses to send credentials unencrypted.
</warning>

### Sending

`email.send()` now suspends until the email was accepted and returns a `SendReceipt` instead of a `Job`. Replace
`email.send().join()` with `email.send()`. `sendSync()` was removed; use `runBlocking { email.send() }` on the JVM if
you really need to block.

### Email builder

| 1.x                                  | 2.0                                   |
|--------------------------------------|---------------------------------------|
| `withSubject("...")`                 | `subject = "..."`                     |
| `withPlainText("...")`               | `text = "..."`                        |
| `withHTMLText("...")`                | `html = "..."`                        |
| `appendText`, `prependText`          | unchanged                             |
| `withAttachment(name, bytes, type)`  | `attach(name, bytes, type)`           |
| `withEmbeddedImage(name, bytes, type)` | `embed(contentId, bytes, type)`     |
| `withHeader(name, value)`            | `header(name, value)`                 |
| `from(InternetAddress(...))`         | `from("Name <address>")`              |
| `email.reply(from = "x") {}`         | `email.reply(from = EmailAddress("x")) {}` |
| `email.forward(from = "x") {}`       | `email.forward(from = EmailAddress("x")) {}` |

### Conversion

`email.eml` still works. Parsing (`String.toEmail()`, `MimeMessage.email`) and `email.mimeMessage` were removed.

### Removed features

The advanced SimpleJavaMail features (S/MIME, DKIM signing, batch module, proxies, calendar invites, ...) are not
part of 2.0. Stay on 1.x if you depend on them.
