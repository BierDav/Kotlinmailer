package at.quickme.kotlinmailer.mime

import at.quickme.kotlinmailer.email.isAscii
import at.quickme.kotlinmailer.email.isVisibleAscii
import kotlin.io.encoding.Base64

internal const val CRLF = "\r\n"

/** RFC 5322 limit for a line without CRLF. */
internal const val MAX_LINE = 998

private const val MAX_QP_LINE = 76

/** Split text into lines, accepting CRLF, LF and CR line endings. */
internal fun String.normalizedLines(): List<String> = replace("\r\n", "\n").replace('\r', '\n').split('\n')

internal fun canUse7Bit(text: String) = text.isAscii() && text.none { it == '\u0000' } &&
    text.normalizedLines().all { it.length <= MAX_LINE }

/** RFC 2045 quoted-printable encoding of UTF-8 text with CRLF line breaks. */
internal fun quotedPrintable(text: String): String {
    val out = StringBuilder()
    for ((index, line) in text.normalizedLines().withIndex()) {
        if (index > 0) out.append(CRLF)
        val bytes = line.encodeToByteArray()
        var lineLength = 0
        for ((i, byte) in bytes.withIndex()) {
            val c = byte.toUByte().toInt().toChar()
            val last = i == bytes.lastIndex
            val literal = c.isVisibleAscii() && c != '=' || (c == ' ' || c == '\t') && !last
            val token = if (literal) c.toString() else "=" + byte.toHexString(HexFormat.UpperCase)
            // keep room for the soft line break "="
            val limit = if (last) MAX_QP_LINE else MAX_QP_LINE - 1
            if (lineLength + token.length > limit) {
                out.append('=').append(CRLF)
                lineLength = 0
            }
            out.append(token)
            lineLength += token.length
        }
    }
    return out.toString()
}

/** Base64 with 76 character lines as required by RFC 2045. */
internal fun base64Lines(bytes: ByteArray): String = Base64.Mime.encode(bytes)
