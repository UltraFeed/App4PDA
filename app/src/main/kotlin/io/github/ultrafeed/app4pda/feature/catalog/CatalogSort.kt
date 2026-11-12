package io.github.ultrafeed.app4pda.feature.catalog

import io.github.ultrafeed.app4pda.data.forum.ForumSearchSort

enum class CatalogSort(val storageValue: Int, val forumSort: ForumSearchSort) {
    NAME(0, ForumSearchSort.RELEVANCE),
    NAME_DESC(1, ForumSearchSort.DATE_DESC),
    RECENTLY_ADDED(2, ForumSearchSort.RELEVANCE);

    fun next() =
        when (this) {
            NAME -> NAME_DESC
            NAME_DESC -> RECENTLY_ADDED
            RECENTLY_ADDED -> NAME
        }

    companion object {
        fun fromStorage(value: Int) = entries.firstOrNull { it.storageValue == value } ?: NAME
    }
}
