package io.github.ultrafeed.app4pda.feature.lookup.data

import io.github.ultrafeed.app4pda.data.forum.ForumPackageLookup

data class AppLookupMatch(val topicId: Int, val packageName: String)

class AppLookupRepository(
    private val lookup: ForumPackageLookup,
) {

    suspend fun find(packageName: String): AppLookupMatch? {
        val match = lookup.find(packageName)
        return match?.let { AppLookupMatch(it.topicId, packageName) }
    }
}
