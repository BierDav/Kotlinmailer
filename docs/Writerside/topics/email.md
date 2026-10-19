# Email API

Emails are immutable and created with the `emailBuilder` DSL.

```kotlin
val email = emailBuilder {
    from("John Doe <john.doe@example.com>")
    to("alice@example.com", name = "Alice")
    cc("bob@example.com")
    bcc("carol@example.com")
    replyTo("support@example.com")

    subject = "Your order"
    text = "Thanks for your order!"
    html = "<p>Thanks for your <b>order</b>!</p>"
}
```

If both `text` and `html` are set, clients show the HTML version and fall back to the text. Non-ASCII content is
encoded automatically. To send the email, go to the [Mailer API](mailer.md); to build HTML with Kotlin, see
[Kotlin HTML DSL](html.md).

## Addresses

Addresses can be given as `john@example.com`, `John Doe <john@example.com>` or as `EmailAddress` instances:

```kotlin
val address = EmailAddress("john@example.com", name = "John Doe")
val parsed = EmailAddress.parse("\"Doe, John\" <john@example.com>")
```

Invalid addresses are rejected with an `IllegalArgumentException`. Internationalized addresses such as
`jörg@bücher.de` are supported, but SMTP servers have to support `SMTPUTF8` to deliver them.

## Attachments

```kotlin
emailBuilder {
    // ...
    attach("invoice.pdf", pdfBytes) // the content type is guessed from the file name
    attach("data.bin", bytes, contentType = "application/octet-stream")
}
```

### Inline images

Embedded files are referenced from the HTML body by their content id:

```kotlin
emailBuilder {
    // ...
    html = """<img src="cid:logo@example.com">"""
    embed("logo@example.com", logoBytes, "image/png")
}
```

## Headers

```kotlin
emailBuilder {
    // ...
    header("List-Unsubscribe", "<https://example.com/unsubscribe>")
    messageId = "order-1234@example.com" // generated if not set
}
```

Headers that Kotlinmailer manages itself, such as `From` or `Content-Type`, cannot be set manually.

## Copy an email

```kotlin
val copiedEmail = email.copy {
    to.clear()
    to("someone.else@example.com")
}
```

The copy gets a new message id.

## Reply to an email

The reply is sent to the `Reply-To` (or `From`) address, keeps the thread headers and quotes the original.

```kotlin
val replyEmail = email.reply(from = EmailAddress("me@example.org"), toAll = false) {
    prependText("Sounds good!")
}
```

## Forward an email

The original email is attached as `message/rfc822`.

```kotlin
val forwardEmail = email.forward(from = EmailAddress("me@example.org")) {
    to("colleague@example.org")
    text = "See below."
}
```
