<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/BierDav/Kotlinmailer/main/.github/images/kotlinmailer_logo_dark.svg">
  <img alt="Kotlinmailer logo" src="https://raw.githubusercontent.com/BierDav/Kotlinmailer/main/.github/images/kotlinmailer_logo_light.svg">
</picture>

Kotlinmailer is a Kotlin Mail API, using coroutines and providing DSLs. It may be used in a Ktor Backend for
verification mails.

This project is a hard fork from [SimpleKotlinMail](https://github.com/jakobkmar/SimpleKotlinMail) which is no more
actively worked on.

## Features

- build emails
- send emails (using an external SMTP server)
- TLS support

## To get started, visit the **[Documentation](https://bierdav.github.io/Kotinmailer/)**.

## Setup

Using Kotlinmailer requires Kotlin compiler `1.4.0` or higher due to the dependency
on [Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization)

### Gradle

Kotlin DSL:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("at.quickme.kotlinmailer:kotlinmailer-core:0.2.0")
    // Optional for Kotlinx HTML DSL support
    implementation("at.quickme.kotlinmailer:kotlinmailer-html:0.2.0")
}
```

Groovy DSL:

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation "at.quickme.kotlinmailer:kotlinmailer-core:0.2.0"
    // Optional for Kotlinx HTML DSL support
    implementation "at.quickme.kotlinmailer:kotlinmailer-html:0.2.0"
}
```

#### JVM Version

To be able to use the inline functions of the API, you have to configure the JVM version (if you have not done that
already).

```kotlin
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = jvmVersionString // <- e.g. 11
}
```

## Examples

The purpose of the following code snippets is to provide an insight into the API. However, they are not suitable for
learning the API, you should use the actual documentation for this.

### Build

Build an email:

```kotlin
val email = emailBuilder {
    from("no-reply@example.com")
    to("foo@bar.com")

    withSubject("Important question")
    withPlainText("Hey, how are you doing?")
}
```

### Send

Send that email:

```kotlin
suspend fun main() = email.send()
```

### Convert

```kotlin
// EML String -> Email
string.toEmail()
// MimeMessage -> Email
mimeMessage.email
```

### HTML

Inside the email builder, you can easily access kotlinx.html:

```kotlin
emailBuilder {
    withHTML {
        div {
            h1 { +"Really important question!" }
            p { +"Hey, how are you doing?" }
        }
    }
}
```

### And more

To learn more about Kotlinmailer, visit the **[Documentation](https://bierdav.github.io/Kotinmailer/)**.

## Project information

This project uses [SimpleJavaMail](https://www.simplejavamail.org/) to deal with java MimeMessages in a more elegant
way.

If you use the documented functionality of SimpleKotlinMail, everything will make use
of [kotlinx.coroutines](https://kotlinlang.org/docs/reference/coroutines-overview.html).
