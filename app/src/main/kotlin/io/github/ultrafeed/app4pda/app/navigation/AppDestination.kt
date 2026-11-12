package io.github.ultrafeed.app4pda.app.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import io.github.ultrafeed.app4pda.core.images.ImageOrigin
import io.github.ultrafeed.app4pda.feature.details.DetailsTab
import io.github.ultrafeed.app4pda.feature.installed.AppSection
import io.github.ultrafeed.app4pda.feature.settings.SettingsPage
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Serializable
sealed interface AppDestination {
    @Serializable data object Home : AppDestination

    @Serializable data object Empty : AppDestination

    @Serializable data class Apps(val section: AppSection) : AppDestination

    @Serializable data object Categories : AppDestination

    @Serializable
    data class Catalog(
        val topic: Int = 0,
        val category: Int = 0,
        val name: String? = null,
        val filter: String? = null,
        val linked: String? = null,
    ) : AppDestination

    @Serializable
    data class Details(
        val topic: Int,
        val packageName: String?,
        val linked: String? = null,
        val initialSource: DetailsTab? = null,
    ) : AppDestination

    @Serializable data object Downloads : AppDestination

    @Serializable data object Digest : AppDestination

    @Serializable
    data class Settings(
        val page: SettingsPage = SettingsPage.ROOT,
    ) : AppDestination

    @Serializable data class Lookup(val packageName: String?) : AppDestination

    @Serializable data class Auth(val addAccount: Boolean = false) : AppDestination

    @Serializable data class Post(val topic: Int, val post: Int, val title: String) : AppDestination

    @Serializable data class Attachment(val topic: Int, val url: String) : AppDestination

    @Serializable
    data class Gallery(
        val images: List<String>,
        val origin: ImageOrigin,
        val page: Int = 0,
    ) : AppDestination
}

@Serializable
enum class ScreenResult {
    AuthChanged,
    DownloadStarted,
}

@Serializable
data class AppEntry(
    val destination: AppDestination,
    val id: String = UUID.randomUUID().toString(),
    val callerId: String? = null,
) : NavKey {
    @Serializable(with = PendingResultSerializer::class)
    private val pendingResult: MutableState<ScreenResult?> = mutableStateOf(null)

    var result: ScreenResult?
        get() = pendingResult.value
        set(value) {
            pendingResult.value = value
        }
}

internal object PendingResultSerializer :
    KSerializer<MutableState<ScreenResult?>> by MutableStateSerializer(
        serializer<ScreenResult?>(),
    )

val AppDestination.isDialog: Boolean
    get() =
        this is AppDestination.Auth ||
            this is AppDestination.Post ||
            this is AppDestination.Attachment

/** These operations edit the standard back stack; there is no second navigation state. */
internal fun MutableList<NavKey>.push(destination: AppDestination) {
    add(AppEntry(destination, callerId = (lastOrNull() as? AppEntry)?.id))
}

internal fun MutableList<NavKey>.pop(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}

internal fun MutableList<NavKey>.complete(senderId: String, result: ScreenResult): Boolean {
    val sender = lastOrNull() as? AppEntry
    if (size <= 1 || sender?.id != senderId) return false
    val receiver = indexOfFirst { (it as AppEntry).id == sender.callerId }
    removeAt(lastIndex)
    if (receiver >= 0) (this[receiver] as AppEntry).result = result
    return true
}

internal fun MutableList<NavKey>.consumeResult(id: String): ScreenResult? {
    val top = (lastOrNull() as? AppEntry)?.takeIf { it.id == id } ?: return null
    val result = top.result
    top.result = null
    return result
}
