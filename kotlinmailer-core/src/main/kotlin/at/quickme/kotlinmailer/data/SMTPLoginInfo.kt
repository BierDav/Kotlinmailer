package at.quickme.kotlinmailer.data

import kotlinx.serialization.Serializable

@Serializable
data class SMTPLoginInfo(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?
)
