# Email API

### Send that email

Go to the [Mailer API](mailer.md).

### Use kotlinx.html

Go to [Kotlin HTML DSL](html.md).

## Copy an email

If you don't want to start blank with your email builder, you can copy another email and change it to your liking.

```kotlin
val copiedEmail = email.copy {
    // modify the email
}
```

## Forward an email

```kotlin
val forwardEmail = email.forward(from = "forwardaddress@example.org") {
    prependText("This is a forwarded message.")
}
```

## Reply to an email

```kotlin
val replyEmail = email.reply(from = "replyaddress@example.org", toAll = false) {
    prependText("This is a reply message.")
}
```