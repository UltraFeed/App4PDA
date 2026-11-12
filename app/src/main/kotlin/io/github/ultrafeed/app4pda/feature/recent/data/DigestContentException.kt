package io.github.ultrafeed.app4pda.feature.recent.data

import java.io.IOException

internal class DigestContentException(message: String) : IOException(message) {
    private companion object {
        private const val serialVersionUID = 1L
    }
}
