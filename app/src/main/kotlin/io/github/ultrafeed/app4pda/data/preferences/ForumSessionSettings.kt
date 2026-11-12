package io.github.ultrafeed.app4pda.data.preferences

import kotlinx.serialization.json.Json

/** Credentials are immutable and intentionally have no generated data-class toString. */
class ForumSessionSettings internal constructor(values: Map<String, Any>, rules: ParsingRules) {
    val cookies: String = (values["cookies"] as? String).orEmpty()
    val username: String = (values["auth_username"] as? String).orEmpty()
    val userId: Int = runCatching {
        Regex(rules["forum_cookie_validate_pattern"])
            .find(cookies)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull() ?: 0
    }
        .getOrDefault(0)
    val authenticated: Boolean
        get() = userId > 0

    val avatarUrl: String? =
        (values[FORUM_ACCOUNTS] as? String)?.let {
            Json.decodeFromString<List<ForumAccount>>(it)
                .firstOrNull { account -> account.id == userId }
                ?.avatarUrl
        }
}
