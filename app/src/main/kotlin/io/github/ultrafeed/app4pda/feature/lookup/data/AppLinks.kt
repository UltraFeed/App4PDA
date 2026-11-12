package io.github.ultrafeed.app4pda.feature.lookup.data

import android.content.Intent
import androidx.core.net.toUri
import io.github.ultrafeed.app4pda.feature.details.DetailsTab
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class StoreAppLink(val packageName: String, val source: DetailsTab)

object AppLinks {
    fun playStoreIntent(packageName: String): Intent =
        Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri(),
        )

    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")

    fun storeFromUrl(value: String): StoreAppLink? {
        val url = value.trim().toHttpUrlOrNull()?.takeIf { it.scheme == "https" } ?: return null
        val source =
            when (url.host) {
                "play.google.com" -> DetailsTab.GOOGLE_PLAY
                "rustore.ru",
                "www.rustore.ru",
                "apps.rustore.ru" -> DetailsTab.RUSTORE
                else -> null
            }
        return source?.let { selected ->
            listingPackage(url, selected)?.takeIf(packagePattern::matches)?.let {
                StoreAppLink(it, selected)
            }
        }
    }

    private fun listingPackage(url: HttpUrl, source: DetailsTab): String? =
        if (source == DetailsTab.GOOGLE_PLAY) {
            url.queryParameter("id").takeIf { url.encodedPath == "/store/apps/details" }
        } else {
            val prefix =
                if (url.host == "apps.rustore.ru") listOf("app") else listOf("catalog", "app")
            url.pathSegments.lastOrNull().takeIf { url.pathSegments.dropLast(1) == prefix }
        }
}
