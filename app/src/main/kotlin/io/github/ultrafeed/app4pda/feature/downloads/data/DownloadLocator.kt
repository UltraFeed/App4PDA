package io.github.ultrafeed.app4pda.feature.downloads.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.net.toUri
import io.github.ultrafeed.app4pda.core.AppPolicy
import io.github.ultrafeed.app4pda.core.network.readForumResponse
import io.github.ultrafeed.app4pda.data.database.DownloadRecord
import io.github.ultrafeed.app4pda.data.forum.ForumRequests
import io.github.ultrafeed.app4pda.data.forum.ForumUrls
import io.github.ultrafeed.app4pda.data.forum.forumSessionCookies
import io.github.ultrafeed.app4pda.data.forum.forumUserAgent
import io.github.ultrafeed.app4pda.data.forum.isForumHost
import io.github.ultrafeed.app4pda.data.forum.validateForumResponse
import io.github.ultrafeed.app4pda.data.googleplay.GooglePlayRepository
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadFile
import io.github.ultrafeed.app4pda.data.googleplay.PlayFileType
import io.github.ultrafeed.app4pda.data.preferences.AppSettings
import io.github.ultrafeed.app4pda.data.rustore.RuStoreRepository
import io.github.ultrafeed.app4pda.feature.details.DetailsFile
import io.github.ultrafeed.app4pda.feature.details.PreparedDownload
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

/** Renew only the originally selected store version and part, never a newer replacement. */
class DownloadLocator(
    private val context: Context,
    private val play: GooglePlayRepository,
    private val ruStore: RuStoreRepository,
    private val settings: AppSettings,
    private val pages: io.ktor.client.HttpClient,
    private val requests: ForumRequests,
) {
    internal suspend fun prepare(file: DetailsFile, pack: String): List<PreparedDownload> {
        return if (file.ruStore) {
            val installed =
                try {
                    context.packageManager.getPackageInfo(pack, 0)
                    true
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                }
            val (info, parts) =
                ruStore.download(pack, context.resources.displayMetrics.densityDpi, !installed)
            parts.mapIndexed { index, part ->
                val apk =
                    PlayDownloadFile(
                        "part-$index.apk",
                        part.url,
                        part.size,
                        PlayFileType.APK,
                        part.hash,
                        "",
                    )
                PreparedDownload(
                    part.url,
                    apk.name,
                    0,
                    0,
                    info.packageName,
                    info.versionCode,
                    apk,
                    "rustore",
                )
            }
        } else if (file.market) {
            val delivery = play.prepareDelivery(pack)
            delivery.files.map { part ->
                PreparedDownload(
                    part.url,
                    part.name,
                    0,
                    0,
                    delivery.release.packageName,
                    delivery.release.versionCode,
                    part,
                )
            }
        } else {
            check(settings.awaitReady().session.authenticated) { "Forum sign-in required" }
            listOf(PreparedDownload(file.url, file.name, file.topicId, file.postId))
        }
    }

    internal suspend fun renew(
        row: DownloadRecord,
        resolveForum: suspend (String) -> String,
    ): String {
        return when (row.source) {
            "play" -> {
                val delivery = play.prepareDelivery(row.packageName)
                check(delivery.release.versionCode == row.versionCode) {
                    "Selected version changed"
                }
                delivery.files
                    .firstOrNull {
                        it.name == row.partName &&
                            (row.checksum.isBlank() ||
                                it.sha256.ifBlank { it.sha1 } == row.checksum)
                    }
                    ?.url ?: error("Selected part missing")
            }

            "rustore" -> {
                val (info, files) =
                    ruStore.download(
                        row.packageName,
                        context.resources.displayMetrics.densityDpi,
                        false,
                    )
                check(info.versionCode == row.versionCode) {
                    "Selected version changed"
                }
                val part =
                    if (row.checksum.isNotBlank()) files.firstOrNull { it.hash == row.checksum }
                    else files.getOrNull(row.partIndex)
                checkNotNull(part).also { check(it.size == row.expectedBytes) }.url
            }

            else -> {
                check(row.locator.isNotBlank()) { "Fresh download link required" }
                if (isForumHost(row.locator.toUri().host.orEmpty())) resolveForum(row.locator)
                else row.locator
            }
        }
    }

    /** Resolve public file URLs without exporting account cookies to the transfer engine. */
    internal suspend fun publicUrl(url: String): String {
        val current = ForumUrls.normalize(url).toUri()
        require(current.scheme == "https") { "HTTPS download required" }
        val session = settings.awaitReady().session
        val publicHeaders =
            mapOf("User-Agent" to forumUserAgent(context), "Accept-Encoding" to "identity")
        val headers = publicHeaders.toMutableMap()
        if (isForumHost(current.host.orEmpty()) && session.cookies.isNotBlank())
            headers["Cookie"] = forumSessionCookies(session.cookies)
        val response =
            requests.execute(true) {
                pages
                    .readForumResponse(
                        "$current",
                        HttpMethod.Head,
                        headers,
                        maxRedirects = AppPolicy.FILE_REDIRECT_LIMIT,
                    )
                    .also { if (isForumHost(it.url.host)) context.validateForumResponse(it) }
            }
        check(response.code in HttpStatusCode.OK.value until HttpStatusCode.MultipleChoices.value) {
            "Download unavailable"
        }
        // Require public access before handing the URL to the cookie-free transfer engine.
        val public =
            if (isForumHost(response.url.host))
                requests.execute(true) {
                    pages
                        .readForumResponse(
                            response.url.toString(),
                            HttpMethod.Head,
                            publicHeaders,
                            maxRedirects = 0,
                        )
                        .also { context.validateForumResponse(it) }
                }
            else
                pages.readForumResponse(
                    response.url.toString(),
                    HttpMethod.Head,
                    publicHeaders,
                    maxRedirects = 0,
                )
        check(public.code in HttpStatusCode.OK.value until HttpStatusCode.MultipleChoices.value) {
            "A public download URL is required"
        }
        return response.url.toString()
    }
}
