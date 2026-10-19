# Render

Kotlinmailer renders emails to standard RFC 5322 / MIME messages itself, on every platform.

`Email` to EML `String`

```kotlin
email.eml
```

`Email` to `RenderedEmail`, which also exposes the UTF-8 `bytes` and whether the message needs the `SMTPUTF8` or
`8BITMIME` SMTP extensions:

```kotlin
val rendered = email.render()
rendered.bytes
```

Bcc recipients are never part of the rendered message; they are only used for the delivery envelope
(`email.recipients`).
