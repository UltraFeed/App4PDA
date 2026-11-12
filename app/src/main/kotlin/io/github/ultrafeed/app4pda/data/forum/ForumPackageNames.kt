package io.github.ultrafeed.app4pda.data.forum

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element

/** Package evidence in message links and labelled text, not in surrounding replies. */
object ForumPackageNames {
    private const val PACKAGE = "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+"
    private val packageName = Regex(PACKAGE)
    private val labelledPackage =
        Regex(
            "(?:Имя\\s+пакета|Название\\s+пакета|package\\s*name|Пакет|ID\\s+приложения)\\s*[:：]?\\s*($PACKAGE)",
            RegexOption.IGNORE_CASE,
        )

    internal fun extract(post: Element?): List<String> {
        if (post == null) return emptyList()
        val names = linkedSetOf<String>()
        post.select("a[href]").forEach { link ->
            val address = link.attr("href")
            val candidate = linkedPackage(address)
            if (candidate != null && packageName.matches(candidate)) names += candidate
        }
        labelledPackage.findAll(post.text()).forEach { names += it.groupValues[1] }
        return names.toList()
    }

    private fun linkedPackage(address: String): String? {
        val url = address.toHttpUrlOrNull()
        return when {
            address.startsWith("market://details?") ->
                address
                    .replaceFirst(
                        "market://",
                        "https://market.android.com/",
                    )
                    .toHttpUrlOrNull()
                    ?.queryParameter("id")

            url == null -> null
            url.host == "play.google.com" && url.encodedPath == "/store/apps/details" ->
                url.queryParameter(
                    "id",
                )

            url.host == "market.android.com" && url.encodedPath == "/details" ->
                url.queryParameter(
                    "id",
                )

            url.host == "f-droid.org" ->
                url.pathSegments.let { parts ->
                    parts.indexOf("packages").takeIf { it >= 0 }?.let { parts.getOrNull(it + 1) }
                }

            url.host == "apps.rustore.ru" ->
                url.pathSegments.let { parts ->
                    parts.indexOf("app").takeIf { it >= 0 }?.let { parts.getOrNull(it + 1) }
                }

            else -> null
        }
    }
}
