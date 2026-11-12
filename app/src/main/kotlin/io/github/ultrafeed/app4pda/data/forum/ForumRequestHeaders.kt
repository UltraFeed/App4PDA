package io.github.ultrafeed.app4pda.data.forum

import android.content.Context
import android.webkit.WebSettings
import org.jsoup.Jsoup

/** The installed engine's real UA; no desktop impersonation or configurable obsolete agent. */
internal fun forumUserAgent(context: Context): String =
    WebSettings.getDefaultUserAgent(context.applicationContext)

internal fun forumSessionCookies(value: String): String =
    value
        .split(';')
        .mapNotNull {
            val name = it.trim().substringBefore('=')
            if (!it.contains('=') || name.startsWith("cf_", true) || name.startsWith("__cf", true))
                null
            else it.trim()
        }
        .joinToString("; ")

internal fun isForumChallenge(mitigated: String?, html: String): Boolean {
    if (mitigated.equals("challenge", true)) return true
    val document = Jsoup.parse(html)
    return document.selectFirst("[id^=post-main-]") == null &&
        (document.select("script").any { it.data().contains("_cf_chl_opt") } ||
            document.selectFirst(
                "#challenge-running, #challenge-stage, script[src*=/cdn-cgi/challenge-platform/]",
            ) != null)
}
