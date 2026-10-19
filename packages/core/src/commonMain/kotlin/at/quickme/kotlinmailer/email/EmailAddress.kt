package at.quickme.kotlinmailer.email

private const val MAX_LOCAL_PART_LENGTH = 64
private const val MAX_DOMAIN_LENGTH = 255
private const val MAX_LABEL_LENGTH = 63
private const val MAX_PATH_LENGTH = 254

private const val ATEXT_SPECIALS = "!#$%&'*+-/=?^_`{|}~"

/**
 * A mailbox, consisting of an [address] (`local@domain`) and an optional display [name].
 *
 * Internationalized addresses (RFC 6532) are allowed; sending them over SMTP requires a server
 * that supports `SMTPUTF8`.
 */
public class EmailAddress(address: String, name: String? = null) {
    /** The `local@domain` part. */
    public val address: String = address.trim()

    /** The optional display name. */
    public val name: String? = name?.trim()?.takeIf { it.isNotEmpty() }

    /** Everything before the last `@`. */
    public val localPart: String

    /** Everything after the last `@`. */
    public val domain: String

    init {
        require(this.name == null || this.name.none { it == '\r' || it == '\n' }) {
            "Display name must not contain line breaks"
        }
        val at = this.address.lastIndexOf('@')
        require(at > 0 && at < this.address.length - 1) { "Invalid email address '$address'" }
        localPart = this.address.substring(0, at)
        domain = this.address.substring(at + 1)
        require(isValidLocalPart(localPart)) { "Invalid local part in email address '$address'" }
        require(isValidDomain(domain)) { "Invalid domain in email address '$address'" }
        require(this.address.encodeToByteArray().size <= MAX_PATH_LENGTH) { "Email address '$address' is too long" }
    }

    /** True if the address contains non-ASCII characters and therefore needs `SMTPUTF8`. */
    public val isInternationalized: Boolean get() = !address.isAscii()

    override fun equals(other: Any?): Boolean =
        other is EmailAddress && other.address.equals(address, ignoreCase = true) && other.name == name

    override fun hashCode(): Int = address.lowercase().hashCode() * 31 + (name?.hashCode() ?: 0)

    /** Human readable form, e.g. `John Doe <john@example.com>`; not encoded for use in headers. */
    override fun toString(): String = if (name == null) address else "$name <$address>"

    public companion object {
        /**
         * Parse `john@example.com`, `John Doe <john@example.com>` or `"Doe, John" <john@example.com>`.
         */
        public fun parse(value: String): EmailAddress {
            val trimmed = value.trim()
            val open = trimmed.lastIndexOf('<')
            if (open < 0 || !trimmed.endsWith(">")) return EmailAddress(trimmed)

            val address = trimmed.substring(open + 1, trimmed.length - 1)
            var name = trimmed.substring(0, open).trim()
            if (name.length >= 2 && name.startsWith('"') && name.endsWith('"')) {
                name = unquote(name.substring(1, name.length - 1))
            }
            return EmailAddress(address, name)
        }

        private fun unquote(value: String) = buildString {
            var escaped = false
            for (c in value) {
                if (!escaped && c == '\\') escaped = true else {
                    append(c)
                    escaped = false
                }
            }
        }
    }
}

internal fun Char.isAtext() = isAsciiLetterOrDigit() || this in ATEXT_SPECIALS || !isAscii()

internal fun isDotAtom(value: String) =
    value.isNotEmpty() && value.split('.').all { atom -> atom.isNotEmpty() && atom.all { it.isAtext() } }

private fun isValidLocalPart(local: String): Boolean {
    if (local.encodeToByteArray().size > MAX_LOCAL_PART_LENGTH) return false
    return isDotAtom(local) || QUOTED_STRING.matches(local)
}

// quoted-string: printable characters, with `"` and `\` escaped
private val QUOTED_STRING = Regex("\"([^\"\\\\\r\n]|\\\\[^\r\n])*\"")

private fun isValidDomain(domain: String): Boolean {
    if (domain.encodeToByteArray().size > MAX_DOMAIN_LENGTH) return false
    if (domain.startsWith('[') && domain.endsWith(']')) {
        // address literal, e.g. [192.0.2.1] or [IPv6:2001:db8::1]
        val literal = domain.substring(1, domain.length - 1)
        return literal.isNotEmpty() && literal.all { it.isVisibleAscii() && it !in "[\\]" }
    }
    return domain.split('.').all { label ->
        label.isNotEmpty() && label.length <= MAX_LABEL_LENGTH &&
            !label.startsWith('-') && !label.endsWith('-') &&
            label.all { it.isAsciiLetterOrDigit() || it == '-' || !it.isAscii() }
    }
}
