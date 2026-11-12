package io.github.ultrafeed.app4pda.feature.auth

import okhttp3.Cookie

enum class ForumAuthChange {
    SIGNED_IN,
    SIGNED_OUT,
}

data class ForumSession(val userId: Int, val username: String, val avatarUrl: String?)

class ForumCaptcha(
    val imageUrl: String?,
    val time: String?,
    val signature: String?,
    internal val cookies: String,
    internal val imageCookies: List<Cookie> = emptyList(),
) {
    override fun toString(): String = "ForumCaptcha(required=${imageUrl != null})"
}
