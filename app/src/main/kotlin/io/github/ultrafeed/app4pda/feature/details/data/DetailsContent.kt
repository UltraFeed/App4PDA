package io.github.ultrafeed.app4pda.feature.details.data

import android.content.Context
import io.github.ultrafeed.app4pda.R
import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.feature.details.AppDetails
import io.github.ultrafeed.app4pda.feature.details.DetailsSection

/** Both early cached content and verified content use exactly the same presentation mapping. */
internal fun forumContent(
    context: Context,
    snapshot: DetailsContext,
    packageName: String,
    loggedIn: Boolean,
    cached: Boolean,
    problem: SourceProblem? = null,
): LoadedDetails {
    val summary = checkNotNull(snapshot.summary)
    val info = snapshot.forum
    val screenshots =
        info
            ?.screenshots
            .orEmpty()
            .filterNot { it.excludedFromGallery }
            .mapNotNull { shot -> shot.galleryUrl?.let { it to (shot.previewUrl ?: it) } }
    return LoadedDetails(
        AppDetails(
            topicId = summary.topicId,
            packageName = packageName,
            title = info?.name.orEmpty().ifBlank { summary.name.ifBlank { packageName } },
            version = summary.version,
            forum =
                DetailsSection(
                    title = context.getString(R.string.source_forum),
                    document = info?.document.orEmpty(),
                    screenshots = screenshots.map { it.first },
                    screenshotPreviews = screenshots.map { it.second },
                    error = info == null,
                    problem = problem,
                ),
            market = null,
            files = emptyList(),
            debugSource = info?.sourceHtml.orEmpty(),
            debugReport = "",
            favorite = summary.favorite,
            cached = cached,
            loggedIn = loggedIn,
            updatedAt = info?.getUpdatedAtMillis() ?: 0,
        ),
        snapshot,
    )
}
