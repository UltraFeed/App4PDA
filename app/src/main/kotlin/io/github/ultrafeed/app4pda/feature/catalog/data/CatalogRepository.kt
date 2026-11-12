package io.github.ultrafeed.app4pda.feature.catalog.data

import io.github.ultrafeed.app4pda.data.database.LocalDatabase
import io.github.ultrafeed.app4pda.data.forum.ForumApiClient
import io.github.ultrafeed.app4pda.data.forum.ForumCatalogPages
import io.github.ultrafeed.app4pda.data.forum.ForumCatalogRequest
import io.github.ultrafeed.app4pda.data.forum.ForumListingPage
import io.github.ultrafeed.app4pda.data.forum.ForumSearchTopic
import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreview
import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreviewResult
import io.github.ultrafeed.app4pda.feature.catalog.CatalogApp
import io.github.ultrafeed.app4pda.feature.catalog.CatalogCategory
import io.github.ultrafeed.app4pda.feature.catalog.CatalogPage
import io.github.ultrafeed.app4pda.feature.catalog.CatalogQuery

class CatalogRepository(
    private val database: LocalDatabase,
    private val api: ForumApiClient,
    private val pages: ForumCatalogPages,
) {
    suspend fun preview(topic: Int): ForumTopicPreview? =
        when (val result = api.topicPreview(topic)) {
            is ForumTopicPreviewResult.Found -> result.preview
            ForumTopicPreviewResult.Missing -> {
                pages.removeTopic(topic)
                null
            }
        }

    suspend fun cached(query: CatalogQuery, page: Int, nextPageUrl: String?): CatalogPage? {
        val cached = pages.cached(query.request(), nextPageUrl) ?: return null
        return catalogPage(cached, page)
    }

    suspend fun load(
        query: CatalogQuery,
        page: Int,
        nextPageUrl: String? = null,
        background: Boolean = false,
    ): CatalogPage {
        require(page >= 0)
        if (query.search?.isBlank() == true) return CatalogPage(emptyList(), page, 0, 0)
        val result =
            if (background) pages.refresh(query.request(), nextPageUrl)
            else pages.load(query.request(), nextPageUrl)
        return catalogPage(result, page)
    }

    private suspend fun catalogPage(
        result: ForumListingPage,
        page: Int,
    ): CatalogPage {
        val categories =
            if (page == 0) result.sections.map { CatalogCategory(it.id, it.name, it.icon) }
            else emptyList()
        return page(result.topics, categories, page, result.nextUrl)
    }

    private suspend fun page(
        topics: List<ForumSearchTopic>,
        categories: List<CatalogCategory>,
        page: Int,
        next: String?,
    ): CatalogPage {
        val favorites = database.users().favoriteTopics().toSet()
        val snapshots = database.forum().snapshots(topics.map { it.id }).associateBy { it.topicId }
        return CatalogPage(
            categories +
                topics.map { topic ->
                    val known = snapshots[topic.id]
                    CatalogApp(
                        "topic:${topic.id}",
                        topic.id,
                        "",
                        topic.name,
                        known?.description?.ifBlank { topic.description } ?: topic.description,
                        known?.version.orEmpty(),
                        favorite = topic.id in favorites,
                        updatedAt = known?.updatedAt ?: 0,
                        requiresAndroidConfirmation = topic.mixedSection,
                        requiresAppConfirmation = topic.applicationCheck,
                    )
                },
            page,
            -1,
            0,
            next,
        )
    }
}

private fun CatalogQuery.request() =
    ForumCatalogRequest(
        groupId,
        search,
        topicId,
        sorting.forumSort,
    )
