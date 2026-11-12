package io.github.ultrafeed.app4pda.data.preferences

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

sealed interface SettingsLoad {
    data object Loading : SettingsLoad

    data class Ready(val settings: SettingsValues) : SettingsLoad

    data class Failed(val cause: Exception) : SettingsLoad
}

class AppSettings(
    private val context: Application,
    internal val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : Closeable {
    val accounts = ForumAccountStore(this)

    private var readJob: Job? = null
    private val writes = Mutex()
    private val mutableState = MutableStateFlow<SettingsLoad>(SettingsLoad.Loading)
    val state = mutableState.asStateFlow()
    val changes = state.filterIsInstance<SettingsLoad.Ready>().map { it.settings }
    val current: SettingsValues
        get() = (state.value as? SettingsLoad.Ready)?.settings ?: error("Settings are not ready")

    init {
        retry()
    }

    @Synchronized
    fun retry() {
        val reading = state.value is SettingsLoad.Loading && readJob?.isActive == true
        if (!scope.isActive || state.value is SettingsLoad.Ready || reading) return
        readJob?.cancel()
        mutableState.value = SettingsLoad.Loading
        readJob = scope.launch {
            try {
                store.data.collect { stored ->
                    val decoded = decode(stored)
                    mutableState.update { it.withValues(decoded, scope.isActive) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                mutableState.update { if (scope.isActive) SettingsLoad.Failed(error) else it }
            } catch (error: IllegalStateException) {
                mutableState.update { if (scope.isActive) SettingsLoad.Failed(error) else it }
            } catch (error: IllegalArgumentException) {
                mutableState.update { if (scope.isActive) SettingsLoad.Failed(error) else it }
            }
        }
    }

    suspend fun awaitReady(): SettingsValues =
        when (val loaded = state.first { it !is SettingsLoad.Loading }) {
            is SettingsLoad.Ready -> loaded.settings
            is SettingsLoad.Failed -> throw loaded.cause
            SettingsLoad.Loading -> error("Unreachable")
        }

    internal fun decode(preferences: Preferences) =
        SettingsValues(context, preferences.asMap().entries.associate { it.key.name to it.value })

    internal suspend fun edit(transform: suspend (MutablePreferences) -> Unit) {
        awaitReady()
        writes.withLock {
            val updated = store.edit(transform)
            currentCoroutineContext().ensureActive()
            val expected = decode(updated).values
            val observed = state.first {
                it is SettingsLoad.Failed ||
                    (it is SettingsLoad.Ready && it.settings.values == expected)
            }
            if (observed is SettingsLoad.Failed) throw observed.cause
        }
    }

    internal suspend fun writeValue(key: String, value: Any?) = edit { prefs ->
        when (value) {
            null -> prefs.remove(stringPreferencesKey(key))
            is String -> prefs[stringPreferencesKey(key)] = value
            is Boolean -> prefs[booleanPreferencesKey(key)] = value
            is Int -> prefs[intPreferencesKey(key)] = value
            is Long -> prefs[longPreferencesKey(key)] = value
            else -> error("Unsupported preference type")
        }
    }

    internal suspend fun ruStoreRetryAt(): Long =
        (awaitReady().values["rustore_retry_at"] as? Long) ?: 0L

    internal suspend fun postponeRuStore(until: Long) = edit {
        val key = longPreferencesKey("rustore_retry_at")
        it[key] = maxOf(it[key] ?: 0L, until)
    }

    internal suspend fun mergeBackup(values: Map<String, Any>, imported: List<ForumAccount>) =
        edit { prefs ->
            for ((key, value) in values) when (value) {
                is Boolean -> prefs[booleanPreferencesKey(key)] = value
                is Int -> prefs[intPreferencesKey(key)] = value
                is String -> prefs[stringPreferencesKey(key)] = value
                else -> error("Invalid backup value")
            }
            val old = decode(prefs)
            val accounts = old.forumAccounts().associateBy { it.id }.toMutableMap()
            imported.forEach { account ->
                val previous = accounts[account.id]
                accounts[account.id] =
                    ForumAccount(
                        account.id,
                        account.name,
                        account.cookies,
                        maxOf(account.searchAvailableAt, previous?.searchAvailableAt ?: 0),
                        account.avatarUrl ?: previous?.avatarUrl,
                        account.email ?: previous?.email,
                        maxOf(account.profileCheckedAt, previous?.profileCheckedAt ?: 0),
                    )
            }
            prefs[stringPreferencesKey(FORUM_ACCOUNTS)] =
                Json.encodeToString(accounts.values.toList())
            val active = accounts[old.session.userId] ?: accounts.values.firstOrNull()
            if (active != null) {
                prefs[stringPreferencesKey("cookies")] = active.cookies
                prefs[stringPreferencesKey("auth_username")] = active.name
            }
        }

    suspend fun setParsingRule(name: String, value: String) =
        writeValue("settings_parse_" + name, value)

    suspend fun setDownloadsOpenMode(value: String) = writeValue("open_downloads", value)

    @Synchronized
    override fun close() {
        scope.cancel()
        mutableState.value = SettingsLoad.Failed(CancellationException("Settings closed"))
    }
}

internal fun createSettingsDataStore(
    context: Context,
    scope: CoroutineScope,
    file: File = context.preferencesDataStoreFile("app-settings"),
): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { file },
    )
}

private fun SettingsLoad.withValues(decoded: SettingsValues, running: Boolean): SettingsLoad =
    if (!running || (this is SettingsLoad.Ready && settings.values == decoded.values)) this
    else SettingsLoad.Ready(decoded)
