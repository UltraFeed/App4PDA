package io.github.ultrafeed.app4pda.feature.updates

import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

internal const val PROJECT_URL = "https://github.com/UltraFeed/App4PDA"
private val stableTag = Regex("""v[0-9]+\.[0-9]+\.[0-9]+""")
private val sha256 = Regex("[0-9a-fA-F]{64}")

internal data class GitHubRelease(val version: String, val digest: String? = null) {
    val apkName = "App4PDA-$version.apk"

    fun asset(name: String) = "$PROJECT_URL/releases/download/v$version/$name"
}

internal class GitHubReleases(private val client: OkHttpClient) {
    fun latest(): GitHubRelease? =
        try {
            apiRelease()
        } catch (_: IOException) {
            redirectRelease()
        } catch (_: IllegalArgumentException) {
            redirectRelease()
        }

    private fun redirectRelease() =
        redirectTag()?.takeIf(stableTag::matches)?.removePrefix("v")?.let { GitHubRelease(it) }

    private fun apiRelease(): GitHubRelease? =
        client
            .newCall(
                Request.Builder()
                    .url("https://api.github.com/repos/UltraFeed/App4PDA/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build(),
            )
            .execute()
            .use { response ->
                if (!response.isSuccessful) throw IOException("GitHub API HTTP ${response.code}")
                Json.parseToJsonElement(response.body.string()).jsonObject.stableRelease()
            }

    private fun redirectTag(): String? {
        val latest = "$PROJECT_URL/releases/latest".toHttpUrl()
        return client
            .newBuilder()
            .followRedirects(false)
            .build()
            .newCall(Request.Builder().url(latest).build())
            .execute()
            .use { response ->
                when {
                    response.code == HTTP_NOT_FOUND -> null
                    response.isRedirect -> {
                        val location = response.header("Location")?.let(latest::resolve)
                        val prefix = "$PROJECT_URL/releases/tag/"
                        val target = location?.toString()
                        if (target?.startsWith(prefix) == true) target.removePrefix(prefix)
                        else if (target == "$PROJECT_URL/releases") null
                        else throw IOException("Unexpected GitHub latest-release redirect")
                    }
                    else -> throw IOException("GitHub latest release HTTP ${response.code}")
                }
            }
    }

    fun checksum(release: GitHubRelease): String =
        release.digest
            ?: client
                .newCall(
                    Request.Builder()
                        .url("$PROJECT_URL/releases/expanded_assets/v${release.version}")
                        .build(),
                )
                .execute()
                .use { response ->
                    if (!response.isSuccessful)
                        throw IOException("GitHub checksum HTTP ${response.code}")
                    Jsoup.parse(response.body.string(), PROJECT_URL)
                        .select("li a[href]")
                        .singleOrNull { it.absUrl("href") == release.asset(release.apkName) }
                        ?.closest("li")
                        ?.select("clipboard-copy[value^=sha256:]")
                        ?.singleOrNull()
                        ?.attr("value")
                        ?.let(::parseDigest)
                        ?: throw IOException("Missing or ambiguous GitHub APK digest")
                }
}

private fun JsonObject.value(key: String) = get(key)?.jsonPrimitive?.content

private fun JsonObject.stableRelease(): GitHubRelease? {
    val tag = value("tag_name") ?: throw IOException("GitHub release tag is missing")
    if (value("draft") != "false" || value("prerelease") != "false" || !stableTag.matches(tag))
        return null
    val release = GitHubRelease(tag.removePrefix("v"))
    val asset =
        (get("assets") as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .singleOrNull { it.value("name") == release.apkName }
            ?: throw IOException("Expected one App4PDA release APK")
    val digest = asset.value("digest")?.let(::parseDigest)
    return release.copy(digest = digest)
}

private fun parseDigest(value: String): String? =
    value.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.takeIf(sha256::matches)

internal fun stableVersion(value: String): List<Int>? =
    value
        .takeIf { stableTag.matches("v$it") }
        ?.split('.')
        ?.map {
            it.toIntOrNull() ?: return null
        }
