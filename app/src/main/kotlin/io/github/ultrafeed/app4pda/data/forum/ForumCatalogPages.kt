package io.github.ultrafeed.app4pda.data.forum

import kotlinx.serialization.json.Json

data class ForumCatalogRequest(
    val section: Int,
    val query: String?,
    val topicId: Int,
    val sorting: ForumSearchSort,
)

interface ForumCatalogPages {
    suspend fun load(request: ForumCatalogRequest, nextUrl: String?): ForumListingPage

    suspend fun cached(request: ForumCatalogRequest, nextUrl: String?): ForumListingPage?

    suspend fun refresh(request: ForumCatalogRequest, nextUrl: String?): ForumListingPage

    suspend fun removeTopic(topic: Int)
}

/** Network selection and HTML parsing stay outside local favorites/card presentation. */
class ForumCatalogPageSource(
    private val api: ForumApiClient,
    private val cache: ForumCatalogCache,
) : ForumCatalogPages {
    override suspend fun load(request: ForumCatalogRequest, nextUrl: String?): ForumListingPage =
        loadPage(request, nextUrl, true)

    override suspend fun refresh(request: ForumCatalogRequest, nextUrl: String?): ForumListingPage =
        loadPage(request, nextUrl, false)

    private suspend fun loadPage(
        request: ForumCatalogRequest,
        nextUrl: String?,
        userInitiated: Boolean,
    ): ForumListingPage {
        val revision = cache.revision()
        val result = fetch(request, nextUrl, userInitiated)
        return cache.save(key(request, nextUrl), result, revision)
    }

    override suspend fun cached(request: ForumCatalogRequest, nextUrl: String?): ForumListingPage? =
        cache.read(key(request, nextUrl))

    override suspend fun removeTopic(topic: Int): Unit = cache.removeTopic(topic)

    private fun key(request: ForumCatalogRequest, nextUrl: String?): String =
        Json.encodeToString(
            listOf(
                request.section.toString(),
                request.query,
                request.topicId.toString(),
                request.sorting.parameter,
                nextUrl,
            ),
        )

    private suspend fun fetch(
        request: ForumCatalogRequest,
        nextUrl: String?,
        userInitiated: Boolean,
    ): ForumListingPage {
        if (request.topicId > 0) return topic(request.topicId, userInitiated)
        val section = request.section.takeIf { it != 0 } ?: ForumSections.APPLICATIONS
        val definition = ForumSections.section(section)
        return when {
            request.query != null || definition.keyword != null -> {
                val result =
                    api.searchTopics(
                        request.query.orEmpty(),
                        request.sorting,
                        nextUrl,
                        request.section,
                        userInitiated = userInitiated,
                    )
                ForumListingPage(emptyList(), result.topics, result.nextUrl)
            }

            else -> listing(definition, nextUrl, userInitiated)
        }
    }

    private suspend fun topic(id: Int, userInitiated: Boolean): ForumListingPage {
        val info =
            try {
                api.loadTopic(id, userInitiated = userInitiated).info
            } catch (failure: ForumApiException) {
                if (!failure.topicMissing) throw failure
                cache.removeTopic(id)
                return ForumListingPage(emptyList(), emptyList(), null)
            }
        return ForumListingPage(
            emptyList(),
            listOf(ForumSearchTopic(info.topicId, info.name, info.getDescription(), false)),
            null,
        )
    }

    private suspend fun listing(
        definition: ForumSection,
        nextUrl: String?,
        userInitiated: Boolean,
    ): ForumListingPage {
        val target = definition.forums.single()
        val url = nextUrl ?: ForumListing.url(target)
        require(ForumListing.id(url, "f") == target)
        val result = ForumListing.parse(api.loadListing(url, userInitiated), target, url)
        return result.copy(
            topics =
                result.topics.map {
                    if (definition.androidOnly) it.copy(mixedSection = true) else it
                },
        )
    }
}
