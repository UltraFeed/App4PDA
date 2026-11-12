package io.github.ultrafeed.app4pda.data.preferences

import kotlinx.serialization.json.Json

/** Service credentials and private profile metadata; diagnostics must never print these fields. */
internal const val FORUM_ACCOUNTS = "forum_accounts"

internal fun SettingsValues.forumAccounts(): List<ForumAccount> {
    val stored =
        (values[FORUM_ACCOUNTS] as? String)
            ?.let {
                Json.decodeFromString<List<ForumAccount>>(it)
            }
            .orEmpty()
    return if (!session.authenticated || stored.any { it.id == session.userId }) stored
    else stored + ForumAccount(session.userId, session.username, session.cookies)
}
