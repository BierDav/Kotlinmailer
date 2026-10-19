# HTML

The `html` module lets you build the HTML body with the DSL of [kotlinx.html](https://github.com/Kotlin/kotlinx.html)
instead of passing in HTML text.

## Set HTML content

Inside your email builder, use the `withHTML` function.

```kotlin
emailBuilder {
    withHTML {
        body {
            h1 { +"Really important question:" }
            p { +"Hey, how are you today?" }
        }
    }
}
```

`appendHTML` and `prependHTML` add content to an existing HTML body.

#### What are the advantages of using kotlinx.html?

In the above example, there isn't any real advantage apart from the better readability. But as soon as you want to
customize the messages, for example for a specific user, kotlinx.html shows its full power.

Let's say that we want to greet the user with their name. Additionally, we want to list the items they bought:

```kotlin
val username = "foo"
val itemsBought = listOf("banana", "apple")

emailBuilder {
    withHTML {
        body {
            h1 { +"We have received your order." }
            p { +"Thanks for shopping with us $username!" }

            p { +"You have bought the following items:" }
            ul {
                for (item in itemsBought)
                    li { +item }
            }
        }
    }
}
```

Here kotlinx.html allows us to use the existing data we have (`username` and `itemsBought`) without the need for an
additional templating language. Text is escaped automatically.
