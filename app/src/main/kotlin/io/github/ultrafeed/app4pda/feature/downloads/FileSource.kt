package io.github.ultrafeed.app4pda.feature.downloads

interface FileSource {
    val topicId: Int

    val postId: Int

    val fileName: String?

    val downloadUrl: String?
}
