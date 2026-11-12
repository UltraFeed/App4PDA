package io.github.ultrafeed.app4pda.feature.settings.data

import android.content.Context
import android.net.Uri
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.packages.PackageInspector
import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.preferences.ForumAccount
import io.github.ultrafeed.app4pda.data.preferences.SettingsDefaults
import io.github.ultrafeed.app4pda.data.preferences.forumAccounts
import io.github.ultrafeed.app4pda.feature.auth.data.ForumAuthRepository
import io.github.ultrafeed.app4pda.feature.catalog.CatalogSort
import io.github.ultrafeed.app4pda.feature.installed.AppPreferences
import io.github.ultrafeed.app4pda.feature.installed.AppPreferencesJson
import io.github.ultrafeed.app4pda.feature.installed.data.InstalledAppsStore
import io.github.ultrafeed.app4pda.feature.installed.data.UpdateSchedule
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

@Serializable
internal class SettingsBackup(
    val settings: JsonObject,
    val accounts: List<ForumAccount>,
    val apps: List<BackupApp>,
)

@Serializable internal data class BackupBinding(val topicId: Int?)

@Serializable
internal data class BackupApp(
    val packageName: String,
    val preferences: JsonObject = JsonObject(emptyMap()),
    val binding: BackupBinding? = null,
)

internal class BackupImport(
    val settings: Map<String, Any>,
    val accounts: List<ForumAccount>,
    val apps: List<BackupApp>,
    val skipped: Int,
) {
    val count
        get() = settings.size + accounts.size + apps.size
}

internal data class BackupResult(val applied: Int, val skipped: Int, val partial: Boolean = false)

/** URI IO stays here; validation does not write either store. */
internal class SettingsBackupRepository(
    private val context: Context,
    private val settings: AppSettings,
    private val database: LocalDatabase,
    private val records: InstalledAppsStore,
    private val packages: PackageInspector,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun export(uri: Uri) =
        withContext(ioDispatcher) {
            val values = settings.awaitReady()
            val installed = packages.scanInstalledPackages().keys
            val exported = buildJsonObject {
                SettingsDefaults.booleans.forEach { (key, _) -> put(key, values.getBoolean(key)) }
                SettingsDefaults.integers.forEach { (key, _) -> put(key, values.getInt(key)) }
                SettingsDefaults.strings.forEach { (key, _) -> put(key, values.getString(key)) }
                values.rules.names.forEach { name ->
                    val key = "settings_parse_$name"
                    (values.values[key] as? String)?.let { put(key, it) }
                }
            }
            val apps =
                database
                    .users()
                    .installed()
                    .filter { it.local != null && it.packageName in installed }
                    .mapNotNull { app ->
                        val flags =
                            AppPreferencesJson.differences(
                                if (app.preferences.hidden) AppPreferences(hidden = true)
                                else app.preferences,
                            )
                        val binding =
                            if (
                                !app.preferences.hidden &&
                                    (app.forum.topicId > 0 || app.bindingSearchCompleted)
                            )
                                BackupBinding(
                                    app.forum.topicId.takeIf { it > 0 },
                                )
                            else null
                        if (flags.isEmpty() && binding == null) null
                        else BackupApp(app.packageName, flags, binding)
                    }
            val bytes =
                json
                    .encodeToString(
                        serializer<SettingsBackup>(),
                        SettingsBackup(exported, values.forumAccounts(), apps),
                    )
                    .toByteArray(Charsets.UTF_8)
            require(bytes.size <= AppPolicy.MAX_BACKUP_BYTES)
            checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use {
                it.write(bytes)
            }
        }

    suspend fun prepare(uri: Uri): BackupImport =
        withContext(ioDispatcher) {
            val bytes =
                checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        require(output.size() + read <= AppPolicy.MAX_BACKUP_BYTES)
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
            require(bytes.size <= AppPolicy.MAX_BACKUP_BYTES)
            validate(bytes.toString(Charsets.UTF_8), packages.scanInstalledPackages().keys)
        }

    private fun validate(text: String, installed: Set<String>): BackupImport {
        val document = json.decodeFromString(serializer<SettingsBackup>(), text)
        require(document.apps.map { it.packageName }.distinct().size == document.apps.size)
        require(document.accounts.map { it.id }.distinct().size == document.accounts.size)
        val (values, skippedSettings) = validateSettings(document.settings)
        val (apps, skippedApps) = validateApps(document.apps, installed)
        return BackupImport(
            values,
            validateAccounts(document.accounts),
            apps,
            skippedSettings + skippedApps,
        )
    }

    private fun validateSettings(input: JsonObject): Pair<Map<String, Any>, Int> {
        var skipped = 0
        val definitions = SettingsSchema.create(context).associateBy { it.key.storageKey }
        val values = buildMap {
            input.forEach { (key, element) ->
                val known =
                    key in SettingsDefaults.booleans ||
                        key in SettingsDefaults.integers ||
                        key in SettingsDefaults.strings
                val rule =
                    key.removePrefix("settings_parse_").takeIf {
                        key.startsWith("settings_parse_") && it in settings.current.rules.names
                    }
                if (!known && rule == null) {
                    skipped++
                    return@forEach
                }
                val primitive = element as? JsonPrimitive ?: error("Invalid setting type")
                val value: Any =
                    when (key) {
                        in SettingsDefaults.booleans -> {
                            require(!primitive.isString)
                            requireNotNull(primitive.booleanOrNull)
                        }

                        in SettingsDefaults.integers -> {
                            require(!primitive.isString)
                            requireNotNull(primitive.intOrNull)
                        }

                        else -> {
                            require(primitive.isString)
                            primitive.content
                        }
                    }
                val definition = definitions[key]
                if (definition != null) require(definition.accepts("$value"))
                if (key == "catalog_sorting")
                    require(
                        CatalogSort.entries.any {
                            it.storageValue == value
                        },
                    )
                put(key, value)
            }
        }

        return values to skipped
    }

    private fun validateAccounts(accounts: List<ForumAccount>): List<ForumAccount> {
        return accounts.onEach { account ->
            require(account.id > 0 && account.searchAvailableAt >= 0)
            require(
                ForumAuthRepository.userId(
                    account.cookies,
                ) == account.id,
            )
            require(
                account.cookies.split(';').none {
                    it.trimStart().startsWith("cf_", true) ||
                        it.trimStart().startsWith("__cf", true)
                },
            )
            require(account.avatarUrl == null || account.avatarUrl.startsWith("https://"))
        }
    }

    private fun validateApps(
        input: List<BackupApp>,
        installed: Set<String>,
    ): Pair<List<BackupApp>, Int> {
        var skipped = 0
        val apps = input.mapNotNull { app ->
            require(app.packageName.isNotBlank())
            val known = AppPreferencesJson.knownFields(app.preferences)
            skipped += app.preferences.size - known.size
            app.binding?.let {
                require(it.topicId == null || it.topicId > 0)
            }
            if (app.packageName !in installed) {
                skipped++
                null
            } else app.copy(preferences = known)
        }
        return apps to skipped
    }

    suspend fun apply(plan: BackupImport): BackupResult =
        withContext(ioDispatcher) {
            val installed = packages.scanInstalledPackages().keys
            val appliedApps =
                records.importPreferences(plan.apps.filter { it.packageName in installed })
            val skipped = plan.skipped + plan.apps.size - appliedApps
            try {
                settings.mergeBackup(plan.settings, plan.accounts)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@withContext BackupResult(appliedApps, skipped, partial = true)
            }
            if (
                plan.settings.keys.any {
                    it == "background_updates" || it == "background_update_interval"
                }
            )
                UpdateSchedule.configure(
                    context,
                    settings.current,
                )
            BackupResult(appliedApps + plan.settings.size + plan.accounts.size, skipped)
        }
}
