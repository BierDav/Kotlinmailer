package at.quickme.kotlinmailer.smtp

private val ENHANCED_STATUS = Regex("^([245])\\.(\\d{1,3})\\.(\\d{1,3})(?: |$)")

/** RFC 5321 reply codes used by the client. */
internal object ReplyCode {
    const val SERVICE_READY = 220
    const val AUTH_SUCCEEDED = 235
    const val OK = 250
    const val WILL_FORWARD = 251
    const val AUTH_CHALLENGE = 334
    const val START_MAIL_INPUT = 354
    const val SERVICE_UNAVAILABLE = 421
    const val FIRST_TRANSIENT = 400
    const val FIRST_PERMANENT = 500
}

/** A (possibly multiline) SMTP reply, RFC 5321 section 4.2. */
internal class SmtpReply(val code: Int, val lines: List<String>) {
    val message: String get() = lines.joinToString(" ").trim()

    /** RFC 3463 enhanced status code, if the server prefixed the reply with one. */
    val enhancedStatusCode: String? get() = lines.firstOrNull()?.let { ENHANCED_STATUS.find(it)?.value?.trim() }

    override fun toString() = "$code $message"

    companion object {
        private const val CODE_LENGTH = 3
        private val VALID_CODES = 200..599

        /** Read one reply from [socket]. */
        suspend fun read(socket: SmtpSocket): SmtpReply {
            val lines = mutableListOf<String>()
            var code: Int? = null
            while (true) {
                val line = socket.readLine() ?: throw SmtpException("Connection closed by server")
                val lineCode = replyCode(line)
                // all lines of a multiline reply must have the same code
                if (lineCode == null || code != null && code != lineCode) {
                    throw SmtpException("Malformed SMTP reply: '$line'")
                }
                code = lineCode
                lines += line.drop(CODE_LENGTH + 1)
                if (line.length == CODE_LENGTH || line[CODE_LENGTH] == ' ') return SmtpReply(lineCode, lines)
            }
        }

        /** The code of a reply line like `250-text` or `250 text`, or null if the line is malformed. */
        private fun replyCode(line: String): Int? {
            val separator = line.getOrNull(CODE_LENGTH)
            if (separator != null && separator != ' ' && separator != '-') return null
            return line.take(CODE_LENGTH).toIntOrNull()?.takeIf { it in VALID_CODES }
        }
    }
}

/** Service extensions announced in the EHLO reply. */
internal class SmtpExtensions(private val keywords: Map<String, List<String>>) {
    operator fun contains(keyword: String) = keyword.uppercase() in keywords

    fun params(keyword: String): List<String> = keywords[keyword.uppercase()].orEmpty()

    val authMechanisms: Set<String> get() = (params("AUTH")).map { it.uppercase() }.toSet()

    /** The maximum message size, `null` if unknown or unlimited. */
    val maxSize: Long? get() = params("SIZE").firstOrNull()?.toLongOrNull()?.takeIf { it > 0 }

    companion object {
        val NONE = SmtpExtensions(emptyMap())

        fun parse(reply: SmtpReply) = SmtpExtensions(
            reply.lines.drop(1).mapNotNull { line ->
                val parts = line.trim().split(' ', '=').filter { it.isNotEmpty() }
                parts.firstOrNull()?.uppercase()?.let { it to parts.drop(1) }
            }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.flatten() },
        )
    }
}
