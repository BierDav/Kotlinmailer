package at.quickme.kotlinmailer.mime

import at.quickme.kotlinmailer.email.EmailAddress
import at.quickme.kotlinmailer.email.isAscii
import at.quickme.kotlinmailer.email.isAsciiControl
import at.quickme.kotlinmailer.email.isAsciiLetterOrDigit
import at.quickme.kotlinmailer.email.isAtext
import at.quickme.kotlinmailer.email.isDotAtom
import kotlin.io.encoding.Base64

private const val MAX_HEADER_LINE = 78

// "=?UTF-8?B?" + "?=" leaves 63 chars for base64 in a 75 char word, i.e. 45 bytes
private const val ENCODED_WORD_BYTES = 45
private const val RFC2231_CHUNK = 60
private const val ATTR_CHAR_SPECIALS = "!#$&+-.^_`|~"

/** True if [value] can't be written as is into an unstructured header. */
internal fun needsEncodedWords(value: String) =
    value.any { it.isAsciiControl() && it != '\t' || !it.isAscii() } || "=?" in value ||
        value.split(' ').any { it.length > MAX_LINE - MAX_HEADER_LINE }

/** RFC 2047 "B" encoded words, separated by spaces so they can be folded. */
internal fun encodeWords(value: String): String {
    val words = mutableListOf<String>()
    val chunk = StringBuilder()
    var chunkBytes = 0
    var i = 0
    while (i < value.length) {
        // never split surrogate pairs, i.e. code points, across words
        val end = if (value[i].isHighSurrogate() && i + 1 < value.length) i + 2 else i + 1
        val codePoint = value.substring(i, end)
        val size = codePoint.encodeToByteArray().size
        if (chunkBytes + size > ENCODED_WORD_BYTES) {
            words += encodedWord(chunk.toString())
            chunk.clear()
            chunkBytes = 0
        }
        chunk.append(codePoint)
        chunkBytes += size
        i = end
    }
    if (chunk.isNotEmpty() || words.isEmpty()) words += encodedWord(chunk.toString())
    return words.joinToString(" ")
}

private fun encodedWord(value: String) = "=?UTF-8?B?${Base64.encode(value.encodeToByteArray())}?="

internal fun unstructured(value: String) = if (needsEncodedWords(value)) encodeWords(value) else value

/** A display name as RFC 5322 phrase: atoms, a quoted-string or encoded words. */
internal fun phrase(name: String): String = when {
    !name.isAscii() || needsEncodedWords(name) -> encodeWords(name)
    name.split(' ').all { word -> word.all { it.isAtext() } } -> name
    else -> "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

internal fun addrSpec(address: EmailAddress): String {
    val local = address.localPart
    val renderedLocal = if (isDotAtom(local) || local.startsWith('"')) local else "\"$local\""
    return "$renderedLocal@${address.domain}"
}

internal fun mailbox(address: EmailAddress): String =
    address.name?.let { "${phrase(it)} <${addrSpec(address)}>" } ?: addrSpec(address)

internal fun addressList(addresses: List<EmailAddress>) = addresses.joinToString(", ", transform = ::mailbox)

/** Render a header field folded at whitespace so lines stay below 78 characters where possible. */
internal fun foldHeader(name: String, value: String): String {
    val out = StringBuilder()
    val line = StringBuilder("$name:")
    for (token in value.split(' ')) {
        // never fold before an empty token, folded lines must not consist of whitespace only
        val hasContent = line.length > name.length + 1
        if (token.isNotEmpty() && hasContent && line.length + 1 + token.length > MAX_HEADER_LINE) {
            out.append(line).append(CRLF)
            line.clear()
        }
        line.append(' ').append(token)
    }
    return out.append(line).append(CRLF).toString()
}

/** A MIME parameter, using RFC 2231 extended notation and continuations if necessary. */
internal fun parameter(name: String, value: String): String {
    val simple = value.isAscii() && value.length <= RFC2231_CHUNK && value.none { it.isAsciiControl() || it in "\"\\" }
    if (simple) return "$name=\"$value\""

    val encoded = value.encodeToByteArray().map { byte ->
        val c = byte.toUByte().toInt().toChar()
        val literal = c.isAsciiLetterOrDigit() || c in ATTR_CHAR_SPECIALS
        if (literal) c.toString() else "%" + byte.toHexString(HexFormat.UpperCase)
    }
    val chunks = mutableListOf(StringBuilder("UTF-8''"))
    for (token in encoded) {
        if (chunks.last().length + token.length > RFC2231_CHUNK) chunks += StringBuilder()
        chunks.last().append(token)
    }
    return if (chunks.size == 1) {
        "$name*=${chunks[0]}"
    } else {
        chunks.withIndex().joinToString("; ") { (i, chunk) -> "$name*$i*=$chunk" }
    }
}
