package io.github.ultrafeed.app4pda.feature.details.data

import io.github.ultrafeed.app4pda.data.database.TopicSummary
import io.github.ultrafeed.app4pda.data.forum.ForumInfo
import io.github.ultrafeed.app4pda.feature.details.AppDetails

/** Action inputs belong to one published document, never to the repository's last request. */
data class DetailsContext(
    val userId: Int,
    val summary: TopicSummary?,
    val forum: ForumInfo?,
    val playPackage: String? = null,
    val playLanguage: String = "",
)

data class LoadedDetails(val data: AppDetails, val context: DetailsContext)
