<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/BierDav/Kotlinmailer/main/.github/images/kotlinmailer_logo_dark.svg">
  <img alt="Kotlinmailer logo" src="https://raw.githubusercontent.com/BierDav/Kotlinmailer/main/.github/images/kotlinmailer_logo_light.svg" height="200px">
</picture>

Kotlinmailer is a Kotlin Multiplatform Mail API, using coroutines and providing DSLs. Send emails from any
application: a Ktor backend, an Android or iOS app, a CLI tool or even the browser.

This project is a hard fork from [SimpleKotlinMail](https://github.com/jakobkmar/SimpleKotlinMail) which is no more
actively worked on.

## Features

- build emails with a DSL, including HTML via kotlinx.html, attachments and inline images
- standards compliant MIME rendering (RFC 5322, 2045-2047, 2231, 6532)
- send emails via SMTP: STARTTLS, implicit TLS, AUTH PLAIN/LOGIN/XOAUTH2/OAUTHBEARER, pipelining, connection pooling
- send emails via JMAP (RFC 8620, RFC 8621)
- no Java Mail dependency, only kotlinx libraries and Ktor

```kotlin
val mailer = smtpMailer("smtp.example.com") {
    credentials("john.doe@example.com", "app-password")
}

emailBuilder {
    from("John Doe <john.doe@example.com>")
    to("alice@example.com")
    subject = "Hello from Kotlin"
    text = "Sent from any platform!"
}.send(mailer)
```

| Module | Platforms                                                                 |
|--------|---------------------------------------------------------------------------|
| `core`, `html`, `jmap` | JVM, JS, Wasm, macOS, iOS, Linux, Windows                  |
| `smtp` | JVM (incl. Android), Node.js, macOS, iOS, Linux and Windows (without TLS)   |

## To get started, visit the **[Documentation](https://bierdav.github.io/Kotlinmailer/)**.

## Setup with Gradle or Maven visit **[Installation](https://bierdav.github.io/Kotlinmailer/modules)**.
