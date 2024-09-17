<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/BierDav/Kotlinmailer/main/.github/images/kotlinmailer_logo_dark.svg">
  <img alt="Kotlinmailer logo" src="https://raw.githubusercontent.com/BierDav/Kotlinmailer/main/.github/images/kotlinmailer_logo_light.svg" height="200px">
</picture>

Kotlinmailer is a Kotlin Mail API, using coroutines and providing DSLs. It may be used in a Ktor Backend for
verification mails.

This project is a hard fork from [SimpleKotlinMail](https://github.com/jakobkmar/SimpleKotlinMail) which is no more
actively worked on.

## Features

- build emails
- send emails (using an external SMTP server)
- TLS support

## To get started, visit the **[Documentation](https://bierdav.github.io/Kotlinmailer/)**.

## Setup with Gradle or Maven visit **[Installation](https://bierdav.github.io/Kotlinmailer/modules)**.

## Project information

This project uses [SimpleJavaMail](https://www.simplejavamail.org/) to deal with java MimeMessages in a more elegant
way.

If you use the documented functionality of SimpleKotlinMail, everything will make use
of [kotlinx.coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html).
