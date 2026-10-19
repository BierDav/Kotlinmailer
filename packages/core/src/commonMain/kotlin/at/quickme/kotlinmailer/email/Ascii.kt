package at.quickme.kotlinmailer.email

internal fun Char.isAscii() = this < '\u0080'

internal fun String.isAscii() = all { it.isAscii() }

/** Printable US-ASCII except space, RFC 5234 `VCHAR`. */
internal fun Char.isVisibleAscii() = this in '!'..'~'

internal fun Char.isAsciiControl() = this < ' ' || this == '\u007F'

internal fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
