package io.github.ultrafeed.app4pda.feature.catalog

import io.github.ultrafeed.app4pda.data.database.TopicSummary
import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreview
import io.github.ultrafeed.app4pda.data.forum.SectionIcon

sealed interface CatalogRow {
    val key: String
}

data class CatalogHeader(override val key: String, val title: String) : CatalogRow

data class CatalogApp(
    override val key: String,
    val topicId: Int,
    val packageName: String,
    val name: String,
    val description: String,
    val version: String,
    val icon: String = "",
    val iconVersion: Int = 0,
    val favorite: Boolean = false,
    val updatedAt: Long = 0,
    val requiresAndroidConfirmation: Boolean = false,
    val requiresAppConfirmation: Boolean = false,
    val summary: TopicSummary = TopicSummary(),
) : CatalogRow

data class CatalogPage(
    val rows: List<CatalogRow>,
    val page: Int,
    val total: Int,
    val limit: Int,
    val nextPageUrl: String? = null,
) {
    val hasMore: Boolean
        get() =
            nextPageUrl != null ||
                (limit > 0 && (page.toLong() + 1) * limit < total && rows.isNotEmpty())
}

data class CatalogCategory(
    val id: Int,
    val name: String,
    val icon: SectionIcon,
) : CatalogRow {
    override val key: String
        get() = "section:$id"
}

data class CatalogUiState(
    val query: CatalogQuery,
    val draft: String = query.search.orEmpty(),
    val rows: List<CatalogRow> = emptyList(),
    val page: Int = -1,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false,
    val searchLimited: Boolean = false,
    val failure: Throwable? = null,
)

internal fun List<CatalogRow>.withTopicPreviews(
    previews: Map<Int, ForumTopicPreview?>,
): List<CatalogRow> = mapNotNull { row ->
    if (row !is CatalogApp) return@mapNotNull row
    if (!previews.containsKey(row.topicId)) return@mapNotNull row
    val preview = previews[row.topicId] ?: return@mapNotNull null
    val unsupportedPlatform = row.requiresAndroidConfirmation && !preview.android
    val unsupportedContent = row.requiresAppConfirmation && !preview.application
    if (unsupportedPlatform || unsupportedContent) {
        null
    } else
        row.copy(
            icon = preview.icon?.takeIf { it.isNotBlank() } ?: row.icon,
            version = preview.version.ifBlank { row.version },
            description = preview.description.ifBlank { row.description },
            updatedAt = preview.updatedAt.takeIf { it > 0 } ?: row.updatedAt,
            requiresAndroidConfirmation = false,
            requiresAppConfirmation = false,
        )
}
