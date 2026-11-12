package io.github.ultrafeed.app4pda.feature.details.data

import android.content.Context
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.data.forum.ForumTopicResult
import io.github.ultrafeed.app4pda.feature.details.DetailsDebugStep

internal data class DetailsDiagnostics(
    val report: String = "",
    val steps: List<DetailsDebugStep> = emptyList(),
    val html: String = "",
)

internal fun ForumTopicResult.detailsDiagnostics(context: Context) =
    DetailsDiagnostics(
        report = context.getString(R.string.debug_html_parse),
        steps =
            diagnostics.map {
                DetailsDebugStep(it.ruleName, it.pattern, it.result.orEmpty(), it.regexStep)
            },
        html = sourceHtml,
    )
