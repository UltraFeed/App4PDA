package io.github.ultrafeed.app4pda.feature.catalog

/** Local catalog query; no remote command or binding by application name. */
data class CatalogQuery(
    val topicId: Int = 0,
    val search: String? = null,
    val groupId: Int = 0,
    val linkedPackage: String? = null,
    val linkedName: String? = null,
    val linkedToForum: Boolean = false,
    val sorting: CatalogSort = CatalogSort.NAME,
)
