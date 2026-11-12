package io.github.ultrafeed.app4pda.data.preferences

import io.github.ultrafeed.app4pda.core.AppPolicy
import kotlinx.serialization.Serializable

@Serializable
class ForumAccount(
    val id: Int,
    val name: String,
    val cookies: String,
    val searchAvailableAt: Long = 0,
    val avatarUrl: String? = null,
    val email: String? = null,
    val profileCheckedAt: Long = 0,
) {
    fun hasFreshProfile(now: Long): Boolean {
        val hasFields = avatarUrl != null && email != null
        return hasFields &&
            profileCheckedAt > 0 &&
            now - profileCheckedAt in 0 until AppPolicy.CACHE_TTL_MILLIS
    }

    override fun toString() = "ForumAccount(id=$id, searchAvailableAt=$searchAvailableAt)"
}
