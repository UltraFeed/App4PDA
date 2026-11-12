package io.github.ultrafeed.app4pda.data.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ultrafeed.app4pda.data.forum.ForumApiException
import io.github.ultrafeed.app4pda.data.forum.ForumFailure
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Account mutations share the application's single DataStore writer and publication barrier. */
class ForumAccountStore internal constructor(private val settings: AppSettings) {
    suspend fun select(id: Int) = settings.edit {
        val account =
            settings.decode(it).forumAccounts().firstOrNull { account -> account.id == id }
                ?: return@edit
        it[stringPreferencesKey("cookies")] = account.cookies
        it[stringPreferencesKey("auth_username")] = account.name
    }

    suspend fun setAutoSwitch(enabled: Boolean) = settings.edit {
        it[booleanPreferencesKey("forum_auto_switch")] = enabled
    }

    suspend fun save(cookies: String, username: String) = settings.edit {
        val previous = settings.decode(it)
        it[stringPreferencesKey("cookies")] = cookies
        it[stringPreferencesKey("auth_username")] = username.trim()
        val actual = settings.decode(it).session
        require(actual.authenticated) { "Forum session has no account identifier" }
        val same = previous.forumAccounts().firstOrNull { account -> account.id == actual.userId }
        val accounts = previous.forumAccounts().filterNot { account -> account.id == actual.userId }
        it[stringPreferencesKey(FORUM_ACCOUNTS)] =
            Json.encodeToString(
                accounts +
                    ForumAccount(
                        actual.userId,
                        username.trim(),
                        cookies,
                        same?.searchAvailableAt ?: 0,
                        same?.avatarUrl,
                        same?.email,
                    ),
            )
    }

    internal suspend fun updateProfile(
        expected: ForumAccount,
        avatar: String?,
        email: String?,
    ) = settings.edit { prefs ->
        val accounts = settings.decode(prefs).forumAccounts()
        prefs[stringPreferencesKey(FORUM_ACCOUNTS)] =
            Json.encodeToString(
                accounts.map {
                    if (it.id == expected.id && it.cookies == expected.cookies)
                        ForumAccount(
                            it.id,
                            it.name,
                            it.cookies,
                            it.searchAvailableAt,
                            avatar ?: it.avatarUrl,
                            email ?: it.email,
                            System.currentTimeMillis(),
                        )
                    else it
                },
            )
    }

    suspend fun remove(id: Int) = settings.edit {
        val values = settings.decode(it)
        val remaining = values.forumAccounts().filterNot { account -> account.id == id }
        it[stringPreferencesKey(FORUM_ACCOUNTS)] = Json.encodeToString(remaining)
        if (values.session.userId == id) {
            it[stringPreferencesKey("cookies")] = remaining.firstOrNull()?.cookies.orEmpty()
            it[stringPreferencesKey("auth_username")] = remaining.firstOrNull()?.name.orEmpty()
        }
    }

    internal suspend fun postponeSearch(id: Int, until: Long) = settings.edit {
        val accounts = settings.decode(it).forumAccounts()
        if (id != 0)
            it[stringPreferencesKey(FORUM_ACCOUNTS)] =
                Json.encodeToString(
                    accounts.map { account ->
                        if (account.id == id)
                            ForumAccount(
                                account.id,
                                account.name,
                                account.cookies,
                                until,
                                account.avatarUrl,
                                account.email,
                                account.profileCheckedAt,
                            )
                        else account
                    },
                )
    }

    internal suspend fun forSearch(): SettingsValues {
        var unavailableUntil = 0L
        var loginRequired = false
        settings.edit {
            val values = settings.decode(it)
            val now = System.currentTimeMillis()
            val accounts = values.forumAccounts()
            if (accounts.isEmpty()) {
                loginRequired = true
            } else {
                val available =
                    accounts.firstOrNull { account ->
                        account.id == values.session.userId && account.searchAvailableAt <= now
                    }
                        ?: accounts.firstOrNull { account ->
                            values.autoSwitchForumAccount && account.searchAvailableAt <= now
                        }
                if (available == null) {
                    unavailableUntil =
                        if (values.autoSwitchForumAccount)
                            accounts.minOf { account -> account.searchAvailableAt }
                        else
                            accounts
                                .firstOrNull { account -> account.id == values.session.userId }
                                ?.searchAvailableAt ?: accounts.first().searchAvailableAt
                } else {
                    it[stringPreferencesKey("cookies")] = available.cookies
                    it[stringPreferencesKey("auth_username")] = available.name
                }
            }
        }
        if (loginRequired)
            throw ForumApiException(
                ForumFailure.LOGIN_REQUIRED,
                "Forum search requires an account",
                HttpStatusCode.Unauthorized.value,
            )
        if (unavailableUntil > 0)
            throw ForumApiException(
                ForumFailure.HTTP,
                "Search cooldown",
                HttpStatusCode.TooManyRequests.value,
                retryAfterMillis = (unavailableUntil - System.currentTimeMillis()).coerceAtLeast(1),
                searchQuota = true,
            )
        return settings.awaitReady()
    }

    suspend fun logout() = remove(settings.awaitReady().session.userId)
}
