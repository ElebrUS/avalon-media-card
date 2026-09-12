package org.ensodai.avalonmediacard.plugins.lampac.data.network

/**
 * Accsdb identity forwarded as query params on every Lampac request.
 *
 * Lampac resolves the user from (in priority order): `token`, `account_email`, `uid`, `box_mac`.
 */
data class LampacAccsdbCredentials(
    val uid: String? = null,
    val token: String? = null,
    val accountEmail: String? = null,
) {
    val isConfigured: Boolean
        get() = !uid.isNullOrBlank() || !token.isNullOrBlank() || !accountEmail.isNullOrBlank()

    companion object {
        val EMPTY = LampacAccsdbCredentials()

        fun fromEnv(): LampacAccsdbCredentials = LampacAccsdbCredentials(
            uid = System.getenv("LAMPAC_UID")?.takeIf { it.isNotBlank() },
            token = System.getenv("LAMPAC_TOKEN")?.takeIf { it.isNotBlank() },
            accountEmail = System.getenv("LAMPAC_ACCOUNT_EMAIL")?.takeIf { it.isNotBlank() },
        )
    }
}
