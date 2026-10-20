<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/BierDav/Kotlinmailer/main/.github/images/kotlinmailer_logo_dark.svg">
    <img alt="Kotlinmailer logo" src="https://raw.githubusercontent.com/BierDav/Kotlinmailer/main/.github/images/kotlinmailer_logo_light.svg" height="200px">
  </picture>
</p>

<p align="center">
  <b>Send emails from any Kotlin platform: a Ktor backend, an Android or iOS app, a CLI tool or even the browser.</b>
</p>

<p align="center">
  <a href="https://central.sonatype.com/namespace/at.quickme.kotlinmailer"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/at.quickme.kotlinmailer/core?label=Maven%20Central&color=7F52FF"></a>
  <a href="https://github.com/BierDav/Kotlinmailer/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/BierDav/Kotlinmailer/actions/workflows/ci.yml/badge.svg?branch=main"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/BierDav/Kotlinmailer?color=blue"></a>
  <a href="https://kotlinlang.org/docs/multiplatform.html"><img alt="Kotlin Multiplatform" src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white"></a>
  <br>
  <img alt="JVM" src="https://img.shields.io/badge/-JVM-DB413D">
  <img alt="Android" src="https://img.shields.io/badge/-Android-6EDB8D">
  <img alt="iOS" src="https://img.shields.io/badge/-iOS-808080">
  <img alt="macOS" src="https://img.shields.io/badge/-macOS-111111">
  <img alt="JS" src="https://img.shields.io/badge/-JS-F8DB5D">
  <img alt="Wasm" src="https://img.shields.io/badge/-Wasm-624FE8">
  <img alt="Linux" src="https://img.shields.io/badge/-Linux-2D3F6C">
  <img alt="Windows" src="https://img.shields.io/badge/-Windows-4D76CD">
</p>

<p align="center">
  <a href="https://bierdav.github.io/Kotlinmailer/"><b>Documentation</b></a> ·
  <a href="https://bierdav.github.io/Kotlinmailer/api/"><b>API reference</b></a> ·
  <a href="https://bierdav.github.io/Kotlinmailer/demo/"><b>Live demo</b></a> ·
  <a href="https://bierdav.github.io/Kotlinmailer/migration.html"><b>Migrating from 1.x</b></a>
</p>

Kotlinmailer is a Kotlin Multiplatform mail library, built on coroutines and DSLs. It has its own SMTP and JMAP clients
and doesn't need Java Mail.

This project is a hard fork of [SimpleKotlinMail](https://github.com/jakobkmar/SimpleKotlinMail), which is no longer
actively maintained.

## Features

- build emails with a DSL, including HTML via kotlinx.html, attachments and inline images
- standards compliant MIME rendering (RFC 5322, 2045-2047, 2231, 6532)
- send emails via SMTP: STARTTLS, implicit TLS, AUTH PLAIN/LOGIN/XOAUTH2/OAUTHBEARER, pipelining, connection pooling
- send emails via JMAP (RFC 8620, RFC 8621), which also works in the browser
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

## Installation

```kotlin
dependencies {
    implementation("at.quickme.kotlinmailer:core:2.0.0")
    implementation("at.quickme.kotlinmailer:smtp:2.0.0") // and/or jmap, html
}
```

| Module                 | Platforms                                                                 |
|------------------------|---------------------------------------------------------------------------|
| `core`, `html`, `jmap` | JVM, JS, Wasm, macOS, iOS, Linux, Windows                                 |
| `smtp`                 | JVM (incl. Android), Node.js, macOS, iOS, Linux and Windows (without TLS) |

`jmap` needs a [Ktor client engine](https://ktor.io/docs/client-engines.html), e.g. `ktor-client-cio` or
`ktor-client-js`. See [Modules](https://bierdav.github.io/Kotlinmailer/modules.html) for Maven and more details.

## Live demo

The [demo](https://bierdav.github.io/Kotlinmailer/demo/) is a [Kobweb](https://kobweb.varabyte.com) website that sends
emails over JMAP straight from your browser, without a backend. SMTP can't work there, because browsers can't open TCP
connections. The source is in [`demo`](demo).

## Contributing

Contributions are very welcome, whether it's code, documentation or a good bug report.

- **Found a bug?** [Open a bug report](https://github.com/BierDav/Kotlinmailer/issues/new?template=bug_report.md).
- **Missing a feature**, e.g. one that 1.x had? [Open a feature request](https://github.com/BierDav/Kotlinmailer/issues/new?template=feature_request.yml),
  or 👍 an [existing one](https://github.com/BierDav/Kotlinmailer/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement).
- **Want to send a pull request?** For larger changes, please open an issue first so we can agree on the approach.

To work on the code you need JDK 21. Then:

```shell
./gradlew check                 # tests on every platform your machine supports, detekt and the API check
./gradlew apiDump               # after changing the public API, commit the updated api/ files
./gradlew :demo:kobwebStart -t  # run the demo at http://localhost:8080/Kotlinmailer/demo/ with live reload
```

- Protocol code goes into `smtp` or `jmap`. `core` stays dependency-light and compiles for every target.
- New behaviour needs tests, preferably in `commonTest`.
- When you change behaviour that users can see, update the docs in [`docs/Writerside`](docs/Writerside).
- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org), e.g. `fix(smtp): ...` or
  `docs: ...`.

More details are in the [contribution guide](https://bierdav.github.io/Kotlinmailer/contributions.html).

## License

Kotlinmailer is licensed under the [Apache License 2.0](LICENSE).
