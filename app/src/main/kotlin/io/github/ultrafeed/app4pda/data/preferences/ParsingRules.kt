package io.github.ultrafeed.app4pda.data.preferences

import io.github.ultrafeed.app4pda.data.forum.ForumUrls

/** Immutable parsing rules for a single operation; an explicit empty override is retained. */
class ParsingRules
internal constructor(
    private val overrides: Map<String, Any>,
) {
    private val defaultParsingRules =
        mapOf(
            "app_type" to
                "<a href=\"http://" +
                    ForumUrls.HOST +
                    "/forum/index.php\\?showforum=213\" style=\"font-weight: bold;text-decoration:none\">Android - Игры</a>",
            "app_group" to "style=\"font-weight: bold;text-decoration:none\">(.+?)</a>",
            "app_name_desc_extra" to
                ">&nbsp;<b>([^<>]+?)(?:- )*(\\[[^<>]+?\\])*</b>(?:, ([^<>]+))*</div></td>",
            "app_dt_upd" to "(?iu)обновление[^>]+?>[^<>]*?(\\d{1,2}\\.\\d{1,2}\\.\\d{1,4})[^<>]*?<",
            "app_scr" to
                "href=\"(http://[^<>]+?\\.(?:png|jpg|gif|jpeg))\".+?<img[^<>]*? src=\"(http://[^<>]+?\\.(?:png|jpg|gif|jpeg))\"",
            "app_ver" to "(?iu)версия[^<>]+?([^<>]+?)<",
            "apk_url" to
                "href=\"([^<>]+?\\.(?:apk|zip|rar))\"(?:[^<>]+?><img[^<>]+?>[^<>]+?</a> \\(([^<>]+?)\\)<span[^<>]+?>([^<>]+))*",
            "app_desc_shot" to
                "(?isu)<b>Краткое описание:</b>\\s*(?:<br\\s*/?>\\s*)*(.*?)(?=<br\\s*/?>\\s*<br\\s*/?>|<b>Описание:</b>|$)",
            "google_play_url" to
                "[(play\\.google\\.com\\/store\\/apps\\/)|(market.android.com\\/)|market:\\/\\/]details\\?id=([^<>]+?)[<|#|\"|&]",
            "post_update" to "Сообщение отредактировал .*? - (.*?, \\d\\d:\\d\\d)",
            "user_icon" to
                """(?s)<div\b[^>]*class=["']photo["'][^>]*>\s*<img\b[^>]*src=["']([^"']+)["']""",
            "login_url" to "https://" + ForumUrls.HOST + "/forum/index.php?act=auth",
            "fav_url" to "https://" + ForumUrls.HOST + "/forum/index.php?act=fav",
            "forum_cookie_validate_pattern" to "member_id=(\\d+)",
            "login_form_captcha_div" to "<div class=\"captcha\">([\\s\\S]*?)<\\/div>",
            "login_form_captcha_url" to "<img[^>]*?src=\"([^\"]+)\"",
            "login_form_captcha_time" to "name=\"captcha-time\"[^>]*?value=\"([^\"]*)\"",
            "login_form_captcha_sig" to "name=\"captcha-sig\"[^>]*?value=\"([^\"]*)\"",
            "login_form_errors" to "<ul class=\"errors-list\">([\\s\\S]*?)</ul>",
            "forum_date_format" to "dd.MM.yy, HH:mm",
            "post_id_from_url" to "^.+?#entry(\\d+?)$",
            "forum_url_find_post" to
                "https://" + ForumUrls.HOST + "/forum/index.php?act=findpost&pid=%post_id%",
            "forum_url_show_topic" to
                "https://" + ForumUrls.HOST + "/forum/index.php?showtopic=%topic_id%",
            "forum_url_show_user" to
                "https://" + ForumUrls.HOST + "/forum/index.php?showuser=%user_id%",
            "forum_url_get_new_post" to
                "https://" +
                    ForumUrls.HOST +
                    "/forum/index.php?showtopic=%topic_id%&view=getnewpost",
        )

    internal val names: Set<String>
        get() = defaultParsingRules.keys

    operator fun get(
        name: String,
    ): String {
        if (name == "forum_domain") return ForumUrls.HOST
        val value =
            (overrides["settings_parse_$name"] as? String) ?: defaultParsingRules[name].orEmpty()
        // Rules can contain literal or regex-escaped hosts. Adapt on read, never migrate storage.
        val current =
            ForumUrls.normalizeRule(
                    value,
                )
                .let {
                    if (
                        name in
                            setOf(
                                "app_type",
                                "app_scr",
                                "user_icon",
                            )
                    )
                        it.replace(
                            "http:",
                            "https?:",
                        )
                    else it
                }
        return if (
            name.endsWith(
                "_url",
            ) ||
                name.startsWith(
                    "forum_url_",
                )
        )
            ForumUrls.normalize(
                current,
            )
        else current
    }
}
