package io.github.ultrafeed.app4pda.data.googleplay

import android.util.Log
import com.aurora.gplayapi.PlayClient
import com.aurora.gplayapi.PlayException
import com.aurora.gplayapi.model.App
import com.aurora.gplayapi.model.AppFile
import com.aurora.gplayapi.model.Delivery
import com.aurora.gplayapi.model.ItemFields
import com.aurora.gplayapi.model.Restriction
import io.github.ultrafeed.app4pda.core.network.SharedRequests
import io.github.ultrafeed.app4pda.core.network.SourceHttpException
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class GooglePlayRepository
internal constructor(
    private val sessions: AnonymousPlaySessionProvider,
    private val pageCache: PlayPageCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutableStatus = MutableStateFlow(PlaySourceStatus())
    val status: StateFlow<PlaySourceStatus> = mutableStatus

    fun requestLocale(): Locale = sessions.requestLocale()

    private suspend fun <T> request(locale: Locale, operation: suspend (PlayClient) -> T): T =
        withContext(ioDispatcher) {
            try {
                val result =
                    try {
                        sessions.execute(locale, operation)
                    } catch (failure: PlayException) {
                        throw failure.asSourceFailure()
                    }
                currentCoroutineContext().ensureActive()
                mutableStatus.value = PlaySourceStatus(true)
                result
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                mutableStatus.value =
                    PlaySourceStatus(problem = error.message ?: "Google Play unavailable")
                throw error
            } catch (error: IllegalArgumentException) {
                currentCoroutineContext().ensureActive()
                mutableStatus.value =
                    PlaySourceStatus(problem = error.message ?: "Google Play unavailable")
                throw error
            }
        }

    private val pageRequests =
        SharedRequests<
            Pair<String, String>,
            PlayAppDetails,
        >()

    suspend fun cachedDetails(
        packageName: String,
        language: String = requestLocale().toLanguageTag(),
    ): PlayAppDetails? = if (packageName.isBlank()) null else pageCache.read(packageName, language)

    suspend fun details(packageName: String): PlayAppDetails = details(packageName, requestLocale())

    private suspend fun details(packageName: String, locale: Locale): PlayAppDetails =
        pageRequests.await(packageName to locale.toLanguageTag()) {
            fetchDetails(packageName, locale)
        }

    private suspend fun fetchDetails(
        packageName: String,
        locale: Locale,
    ): PlayAppDetails {
        val revision = pageCache.revision()
        return try {
            val requested =
                request(locale) { client ->
                    client.apps.details(packageName, ItemFields.FULL).toPlayDetails()
                }
            val details =
                if (locale.language != "en" && requested.description.isBlank()) {
                    englishDescription(requested, revision)
                } else requested
            pageCache.save(details, locale.toLanguageTag(), revision)
            details
        } catch (failure: PlaySourceException.NotFound) {
            pageCache.remove(packageName)
            throw failure
        }
    }

    private suspend fun englishDescription(
        requested: PlayAppDetails,
        revision: Long,
    ): PlayAppDetails =
        try {
            val english =
                request(Locale.ENGLISH) { client ->
                    client.apps
                        .details(requested.release.packageName, ItemFields.FULL)
                        .toPlayDetails()
                }
            pageCache.save(english, "en", revision)
            requested.copy(description = english.description, changes = english.changes)
        } catch (failure: IOException) {
            Log.w("GooglePlay", "English description unavailable", failure)
            requested
        } catch (failure: IllegalArgumentException) {
            Log.w("GooglePlay", "Invalid English description", failure)
            requested
        }

    private val versionRequests = SharedRequests<Pair<List<String>, String>, MarketBatch>()

    suspend fun versions(packages: List<String>): MarketBatch {
        val locale = requestLocale()
        val names = packages.distinct().sorted()
        return versionRequests.await(names to locale.toLanguageTag()) {
            fetchVersions(names, locale)
        }
    }

    private suspend fun fetchVersions(packages: List<String>, locale: Locale): MarketBatch {
        val revision = pageCache.revision()
        val items = mutableListOf<PlayAppDetails>()
        val errors = mutableListOf<String>()
        var failure: Exception? = null
        for (chunk in packages.distinct().chunked(DETAILS_BATCH_SIZE)) {
            try {
                val found =
                    request(locale) { client ->
                        client.apps.details(chunk, ItemFields.FULL).map(App::toPlayDetails)
                    }
                found.forEach { pageCache.save(it, locale.toLanguageTag(), revision) }
                items += found
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: PlaySourceException.RateLimited) {
                failure = error
                errors += error.message.orEmpty()
                break
            } catch (error: IOException) {
                if (failure == null) failure = error
                errors += error.message ?: "Google Play unavailable"
            } catch (error: IllegalArgumentException) {
                if (failure == null) failure = error
                errors += error.message ?: "Google Play unavailable"
            }
        }
        return MarketBatch(items, errors, failure = failure)
    }

    suspend fun downloads(packageName: String): List<PlayDownloadFile> =
        prepareDelivery(packageName).files

    suspend fun prepareDelivery(packageName: String): PlayDelivery {
        val locale = requestLocale()
        val app = details(packageName, locale)
        if (!app.free) throw PlaySourceException.Paid()
        if (app.restricted) throw PlaySourceException.Restricted()
        val delivery =
            request(locale) { client ->
                client.delivery.resolveDelivery(
                    packageName = packageName,
                    versionCode = app.release.versionCode,
                    offerType = app.offerType,
                    patchFormats = emptyList(),
                )
            }
        return delivery.toPlayDelivery(app.release)
    }
}

// Bound a single Play details batch while retaining per-package outcomes.
private const val DETAILS_BATCH_SIZE = 20

internal fun App.toPlayDetails() =
    PlayAppDetails(
        PlayRelease(packageName, versionName, versionCode),
        displayName,
        description,
        changes,
        screenshots.map { it.url },
        isFree,
        restriction != Restriction.AVAILABLE,
        offerType,
        size,
    )

internal fun Delivery.toPlayDelivery(release: PlayRelease): PlayDelivery {
    val problem =
        when {
            versionCode != 0L && versionCode != release.versionCode ->
                "Google Play served a different release"
            encryption != null -> "Google Play delivery needs unsupported encryption"
            files.none { it.type == AppFile.Type.BASE } -> "Google Play delivery has no base APK"
            else -> null
        }
    if (problem != null) throw PlaySourceException.Unavailable(problem)
    return PlayDelivery(release, files.map(AppFile::toDownloadFile))
}

private fun AppFile.toDownloadFile() =
    PlayDownloadFile(
        name,
        url,
        size,
        when (type) {
            AppFile.Type.BASE -> PlayFileType.BASE
            AppFile.Type.SPLIT -> PlayFileType.SPLIT
            AppFile.Type.OBB -> PlayFileType.OBB
            AppFile.Type.PATCH -> PlayFileType.PATCH
        },
        sha256,
        sha1,
    )

internal fun PlayException.asSourceFailure(): IOException =
    when (this) {
        is PlayException.NotFound -> PlaySourceException.NotFound()
        is PlayException.Unauthorized -> PlaySourceException.Authentication()
        is PlayException.AppNotPurchased,
        is PlayException.AppNotSupported,
        is PlayException.RequiresAccount -> PlaySourceException.Restricted()
        is PlayException.RateLimited -> PlaySourceException.RateLimited()

        is PlayException.ServerError -> SourceHttpException(code)
        is PlayException.AppRemoved,
        is PlayException.NoFilesAvailable,
        is PlayException.Unknown ->
            PlaySourceException.Unavailable(
                "Google Play request failed",
            )
    }.also { it.initCause(this) }
