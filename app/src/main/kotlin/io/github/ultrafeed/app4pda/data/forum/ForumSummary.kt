package io.github.ultrafeed.app4pda.data.forum

interface ForumSummary {
    fun getId(): Int

    fun getType(): Int

    fun getUpdatedAtMillis(): Long

    fun getDescription(): String?

    fun getVersionName(): String?
}
