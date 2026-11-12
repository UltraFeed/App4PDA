package io.github.ultrafeed.app4pda.feature.details

import io.github.ultrafeed.app4pda.core.network.SourceProblem
import io.github.ultrafeed.app4pda.data.googleplay.PlayDownloadFile
import kotlinx.serialization.Serializable

@Serializable
enum class DetailsTab(val savedId: Int) {
    FORUM(0),
    GOOGLE_PLAY(1),
    RUSTORE(2);

    companion object {
        fun fromSaved(id: Int?): DetailsTab = entries.firstOrNull { it.savedId == id } ?: FORUM
    }
}

/** Presentation data contains no Android Views or screen objects. */
data class DetailsFile(
    val id: String,
    val name: String,
    val version: String,
    val size: String,
    val url: String,
    val topicId: Int,
    val postId: Int,
    val details: String,
    val update: Boolean,
    val market: Boolean = false,
    val ruStore: Boolean = false,
    val packageName: String = "",
)

data class DetailsSection(
    val title: String,
    val document: String,
    val screenshots: List<String> = emptyList(),
    val error: Boolean = false,
    val problem: SourceProblem? = null,
    val screenshotPreviews: List<String> = screenshots,
)

data class AppDetails(
    val topicId: Int,
    val packageName: String,
    val title: String,
    val version: String,
    val forum: DetailsSection,
    val market: DetailsSection?,
    val files: List<DetailsFile>,
    val debugSource: String,
    val debugReport: String,
    val favorite: Boolean,
    val cached: Boolean = false,
    val loggedIn: Boolean = true,
    val downloadDirectoryReady: Boolean = true,
    val updatedAt: Long = 0,
    val debugSteps: List<DetailsDebugStep> = emptyList(),
    val debugHtml: String = "",
    val ruStore: DetailsSection? = null,
)

fun AppDetails.availableTabs(): List<DetailsTab> = buildList {
    if (topicId > 0) add(DetailsTab.FORUM)
    if (market?.error == false) add(DetailsTab.GOOGLE_PLAY)
    if (ruStore?.error == false) add(DetailsTab.RUSTORE)
}

data class AppDetailsUiState(
    val data: AppDetails? = null,
    val loading: Boolean = false,
    val playLoading: Boolean = false,
    val error: Boolean = false,
    val actionBusy: Boolean = false,
    val actionError: Boolean = false,
    val actionProblem: SourceProblem? = null,
    val tab: DetailsTab = DetailsTab.FORUM,
    val selectedFileId: String? = null,
)

data class PreparedDownload(
    val url: String,
    val name: String,
    val topicId: Int,
    val postId: Int,
    val playPackage: String = "",
    val playVersion: Long = 0,
    val playFile: PlayDownloadFile? = null,
    val storeSource: String = "play",
)

data class DetailsDebugStep(
    val title: String,
    val pattern: String,
    val result: String,
    val regex: Boolean,
)

// The saved tab identity is independent of the visible pager position.
