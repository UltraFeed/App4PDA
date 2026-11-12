package io.github.ultrafeed.app4pda.data.database

import io.github.ultrafeed.app4pda.data.forum.ForumInfo

data class TopicSummary(
    val topicId: Int = 0,
    val packageName: String = "",
    val name: String = "",
    val description: String = "",
    val version: String = "",
    private val forumUpdatedAt: Long = 0,
    val favorite: Boolean = false,
) {
    fun withForumInfo(info: ForumInfo): TopicSummary =
        copy(
            packageName = packageName.ifBlank { info.getPrimaryPackageName().orEmpty() },
            name = info.name.ifBlank { name },
            description = info.getDescription(),
            version = info.getVersionName().ifBlank { version },
            forumUpdatedAt = info.getUpdatedAtMillis(),
        )
}
