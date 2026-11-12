package io.github.ultrafeed.app4pda.data.forum

import android.text.Html
import io.github.ultrafeed.app4pda.core.formatting.DisplayFormats
import io.github.ultrafeed.app4pda.data.preferences.SettingsValues
import io.github.ultrafeed.app4pda.feature.gallery.ScreenshotInfo
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.jsoup.Jsoup

enum class ForumDiagnosticGroup {
    TOPIC,
}

data class ForumParseDiagnostic(
    val group: ForumDiagnosticGroup,
    val ruleName: String,
    val pattern: String,
    val matched: Boolean,
    val result: String? = null,
    val regexStep: Boolean = true,
)

data class ForumTopicParseResult(val info: ForumInfo?, val diagnostics: List<ForumParseDiagnostic>)

/** Parses a topic using a fixed snapshot of local regex and replacement rules. */
class ForumTopicParser(rules: Map<String, String>) {
    private val rules = rules.toMap()

    fun parse(
        topicId: Int,
        html: String,
        overrides: Map<String, String> = emptyMap(),
    ): ForumTopicParseResult = parse(topicId, html, ForumPage.parse(html), overrides)

    internal fun parse(
        topicId: Int,
        html: String,
        page: ForumPage,
        overrides: Map<String, String> = emptyMap(),
    ): ForumTopicParseResult {
        return TopicExtraction(topicId, html, page, rules, overrides).parse()
    }

    companion object {
        private val ruleNames =
            listOf(
                "app_type",
                "app_group",
                "app_name_desc_extra",
                "app_dt_upd",
                "google_play_url",
                "app_scr",
                "app_ver",
                "apk_url",
                "app_user_guest",
                "post_update",
                "forum_date_format",
                "app_desc_shot",
            )

        @JvmStatic
        fun fromSettings(
            settings: SettingsValues,
        ): ForumTopicParser = ForumTopicParser(ruleNames.associateWith(settings.rules::get))
    }
}

/** One request owns its evidence and diagnostic order; no parser state survives between pages. */
private class TopicExtraction(
    private val topicId: Int,
    private val html: String,
    private val page: ForumPage,
    private val rules: Map<String, String>,
    private val overrides: Map<String, String>,
) {
    private val evaluation = TopicRuleEvaluation(html, rules, overrides)
    private val selectedPost = page.post

    fun parse(): ForumTopicParseResult {
        val info =
            try {
                val header = header()
                val packages = packages()
                val images = screenshots()
                val versionName = version()
                val attachments = attachments()
                diagnosticState(attachments.matched)
                val description = description()
                val body = body()
                ForumInfo(
                        ForumTopicHeader(
                            topicId,
                            header.title.name,
                            packages,
                            header.title.extra,
                            versionName,
                            description.ifBlank { header.title.summary },
                            header.group,
                            if (header.game) 1 else 0,
                            evaluation.flags,
                        ),
                        images,
                        body.html,
                        attachments.files,
                    )
                    .apply {
                        sourceHtml = html
                        postId = body.postId
                        postUpdatedAtMillis = body.updatedAt
                        shortDescription = description
                    }
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IndexOutOfBoundsException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
        return ForumTopicParseResult(info, evaluation.diagnostics.toList())
    }

    private fun header(): TopicHeader {
        val type = evaluation.matcher("app_type")
        val game = type.find()
        evaluation.record("app_type", type, game, 2)

        val groupMatch = evaluation.matcher("app_group")
        val hasGroup = groupMatch.find()
        val groupName = if (hasGroup) stripTags(group(groupMatch)) else ""
        evaluation.record("app_group", groupMatch, hasGroup, GROUP_MATCH)

        val title = title()
        val updated = evaluation.matcher("app_dt_upd")
        val hasUpdated = updated.find()
        val parsedUpdatedDate =
            if (hasUpdated) ForumDates.parse(stripTags(group(updated)), "dd.MM.yyyy") else null
        val updatedDate = DisplayFormats.date(parsedUpdatedDate, "yyyyMMdd").toIntOrNull() ?: 0
        evaluation.record("app_dt_upd", updated, hasUpdated, DATE_MATCH, "app_date_update")

        return TopicHeader(game, groupName, title, ForumDates.parseCompactDate(updatedDate))
    }

    private fun title(): TopicTitle {
        val title = evaluation.matcher("app_name_desc_extra")
        val hasTitle = title.find()
        val name =
            if (hasTitle) decode(group(title)).replace("'", "\"").trim()
            else
                page.document.selectFirst("h1")?.text().orEmpty().ifBlank {
                    selectedPost?.selectFirst("b,strong")?.text().orEmpty()
                }
        val extra =
            if (hasTitle)
                decode(group(title, 2)).replace("'", "\"").trim().replace("[Android OS]", "").trim()
            else ""
        val summary = if (hasTitle) decode(group(title, 3)).replace("'", "\"").trim() else ""
        evaluation.record("app_name_desc_extra", title, hasTitle, TITLE_MATCH)

        return TopicTitle(name, extra, summary)
    }

    private fun packages(): List<String> {
        val packages = mutableListOf<String>()
        val playUrl = evaluation.matcher("google_play_url", selectedPost?.outerHtml().orEmpty())
        var hasPlayUrl = false
        while (playUrl.find()) {
            playUrl.group(1)?.let { if (it !in packages) packages += it }
            hasPlayUrl = true
        }
        evaluation.record("google_play_url", playUrl, hasPlayUrl, PACKAGE_MATCH)
        if (!overrides.containsKey("google_play_url"))
            ForumPackageNames.extract(selectedPost).forEach {
                if (it !in packages) packages += it
            }

        return packages
    }

    private fun screenshots(): List<ScreenshotInfo> {
        val images = mutableListOf<ScreenshotInfo>()
        val screenshot = evaluation.matcher("app_scr")
        while (screenshot.find()) {
            images +=
                ScreenshotInfo(
                    previewUrl = screenshot.group(2)?.let(ForumUrls::normalize),
                    fullUrl = screenshot.group(1)?.let(ForumUrls::normalize),
                )
        }
        evaluation.record("app_scr", screenshot, images.isNotEmpty(), SCREENSHOT_MATCH)
        if (!overrides.containsKey("app_scr") && selectedPost != null) {
            images.clear()
            images += ForumTopicIcon.extract(selectedPost).screenshots
        }

        return images
    }

    private fun version(): String {
        val version = evaluation.matcher("app_ver")
        val hasVersion = version.find()
        val versionName =
            if (hasVersion) decode(stripTags(group(version)).trim()).replace("\\", "/") else ""
        evaluation.record("app_ver", version, hasVersion, VERSION_MATCH)

        return versionName
    }

    private fun attachments(): TopicAttachments {
        val files = mutableListOf<ForumFileInfo>()
        val fileTopicId = topicId
        val attachment = evaluation.matcher("apk_url", selectedPost?.outerHtml().orEmpty())
        var hasAttachmentMatch = false
        while (attachment.find()) {
            attachment.group(1)?.let { url ->
                files +=
                    ForumFileInfo().apply {
                        setDownloadUrl(stripTags(url))
                        setDisplaySize(attachment.group(2))
                        setDownloadCount(attachment.group(ATTACHMENT_DOWNLOAD_COUNT))
                        this.topicId = fileTopicId
                    }
            }
            hasAttachmentMatch = true
        }
        evaluation.record("apk_url", attachment, hasAttachmentMatch, ATTACHMENT_MATCH)

        return TopicAttachments(files, hasAttachmentMatch)
    }

    private fun diagnosticState(hasAttachmentMatch: Boolean) {
        val guest = evaluation.matcher("app_user_guest")
        val guestView = guest.pattern().pattern().isNotEmpty() && guest.find()
        evaluation.record("app_user_guest", guest, guestView)
        // The server diagnostic record historically assigns this bit from APK matches.
        if (hasAttachmentMatch) evaluation.flags += ATTACHMENT_DIAGNOSTIC

        val postUpdate =
            evaluation.matcher("post_update", selectedPost?.selectFirst(".edit")?.text().orEmpty())
        val hasPostUpdate = postUpdate.find()
        evaluation.record("post_update", postUpdate, hasPostUpdate, EDIT_MATCH)
    }

    private fun description(): String {
        var shortDescription = ""
        val short = evaluation.matcher("app_desc_shot")
        val hasShort = short.groupCount() > 0 && short.find()
        if (hasShort) shortDescription = decode(group(short)).trim().substringBefore("\n\n")
        evaluation.record("app_desc_shot", short, hasShort, DESCRIPTION_MATCH)

        return shortDescription
    }

    private fun body(): TopicBody {
        val hasPost = selectedPost != null
        val postId = selectedPost?.id()?.removePrefix("post-main-")?.toIntOrNull() ?: 0
        val parsedPost =
            if (postId > 0)
                ForumPostParser(rule = { key -> rules[key].orEmpty() })
                    .parse(
                        page,
                        topicId,
                    )
            else null
        val bodyHtml = ForumDocumentParser.parse(parsedPost?.html.orEmpty())
        evaluation.diagnostics +=
            ForumParseDiagnostic(
                ForumDiagnosticGroup.TOPIC,
                "app_desc_full",
                "DOM post-main",
                bodyHtml.isNotBlank(),
                result = bodyHtml,
                regexStep = false,
            )
        evaluation.diagnostics +=
            ForumParseDiagnostic(
                ForumDiagnosticGroup.TOPIC,
                "post_id",
                "DOM post-main",
                hasPost,
                result = "$postId",
                regexStep = false,
            )
        if (hasPost) evaluation.flags += POST_MATCH

        return TopicBody(postId, bodyHtml, parsedPost?.updatedAt ?: 0)
    }
}

private data class TopicTitle(val name: String, val extra: String, val summary: String)

private data class TopicHeader(
    val game: Boolean,
    val group: String,
    val title: TopicTitle,
    val updatedAt: Long,
)

private data class TopicAttachments(val files: List<ForumFileInfo>, val matched: Boolean)

private data class TopicBody(val postId: Int, val html: String, val updatedAt: Long)

private class TopicRuleEvaluation(
    private val html: String,
    private val rules: Map<String, String>,
    private val overrides: Map<String, String>,
) {
    val diagnostics = mutableListOf<ForumParseDiagnostic>()
    var flags = 0

    fun matcher(key: String, text: String = html): Matcher =
        Pattern.compile(
                if (overrides.containsKey(key)) overrides.getValue(key) else rules[key].orEmpty(),
            )
            .matcher(text)

    fun record(
        key: String,
        expression: Matcher,
        matched: Boolean,
        bit: Int = 0,
        diagnosticName: String = key,
    ) {
        diagnostics +=
            ForumParseDiagnostic(
                ForumDiagnosticGroup.TOPIC,
                diagnosticName,
                expression.pattern().pattern(),
                matched,
            )
        if (matched) flags += bit
    }
}

internal fun group(expression: Matcher, index: Int = 1) = expression.group(index).orEmpty()

internal fun stripTags(text: String) = Jsoup.parseBodyFragment(text).text().replace("'", "\"")

internal fun decode(text: String) = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()

// Bit assignments are retained in parsed cache diagnostics.
private const val GROUP_MATCH = 4
private const val TITLE_MATCH = 8
private const val VERSION_MATCH = 16
private const val DATE_MATCH = 32
private const val SCREENSHOT_MATCH = 64
private const val ATTACHMENT_MATCH = 128
private const val PACKAGE_MATCH = 256
private const val DESCRIPTION_MATCH = 512
private const val POST_MATCH = 2048
private const val EDIT_MATCH = 4096
private const val ATTACHMENT_DIAGNOSTIC = 8192
private const val ATTACHMENT_DOWNLOAD_COUNT = 3
