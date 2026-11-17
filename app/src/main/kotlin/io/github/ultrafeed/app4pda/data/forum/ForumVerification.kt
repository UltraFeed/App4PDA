package io.github.ultrafeed.app4pda.data.forum

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One foreground verification at a time. Browser clearance is never an account session. */
internal object ForumVerification {
    data class Request(val url: String, val result: CompletableDeferred<String?>)

    private val mutex = Mutex()
    private val pending = MutableStateFlow<Request?>(null)
    val requests = pending.asStateFlow()
    @Volatile var foreground = false
    @Volatile
    var clearance: String? = null
        private set

    suspend fun verify(url: String): Boolean = mutex.withLock {
        if (!foreground) return@withLock false
        val request = Request(url, CompletableDeferred())
        pending.value = request
        try {
            val cookie = request.result.await() ?: return@withLock false
            clearance = cookie
            true
        } finally {
            pending.value = null
        }
    }
}
