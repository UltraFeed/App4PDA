package io.github.ultrafeed.app4pda.data.googleplay

import io.github.ultrafeed.app4pda.core.network.HttpStatusFailure
import java.io.IOException
import kotlinx.serialization.Serializable

@Serializable
data class PlayRelease(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class PlayAppDetails(
    val release: PlayRelease,
    val name: String,
    val description: String,
    val changes: String,
    val screenshots: List<String>,
    val free: Boolean,
    val restricted: Boolean,
    val offerType: Int,
    val size: Long,
)

data class PlayDelivery(val release: PlayRelease, val files: List<PlayDownloadFile>)

enum class PlayFileType {
    APK,
    BASE,
    SPLIT,
    OBB,
    PATCH,
}

data class PlayDownloadFile(
    val name: String,
    val url: String,
    val size: Long,
    val type: PlayFileType,
    val sha256: String,
    val sha1: String,
)

data class PlaySourceStatus(val connected: Boolean = false, val problem: String? = null)

data class MarketBatch(
    val items: List<PlayAppDetails>,
    val errors: List<String> = emptyList(),
    val unavailablePackages: Set<String> = emptySet(),
    val failure: Throwable? = null,
)

sealed class PlaySourceException(message: String) : IOException(message) {
    private companion object {
        private const val serialVersionUID = 1L
    }

    class NotFound : PlaySourceException("Application not found"), HttpStatusFailure {
        private companion object {
            private const val serialVersionUID = 1L
        }

        override val httpStatus: Int = 404
    }

    class Authentication : PlaySourceException("Anonymous Google Play session expired") {
        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    class RateLimited :
        PlaySourceException("Google Play rate limit; retry later"), HttpStatusFailure {
        private companion object {
            private const val serialVersionUID = 1L
        }

        override val httpStatus: Int = 429
    }

    class Unavailable(message: String) : PlaySourceException(message) {
        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    class Restricted : PlaySourceException("Application is unavailable for this device or region") {
        private companion object {
            private const val serialVersionUID = 1L
        }
    }

    class Paid : PlaySourceException("Anonymous download is available only for free applications") {
        private companion object {
            private const val serialVersionUID = 1L
        }
    }
}
