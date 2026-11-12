package io.github.ultrafeed.app4pda.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A request lives until its last consumer leaves, independently of the first consumer's Job. */
internal class SharedRequests<K, V> {
    private class Request<V>(val owner: Job, val result: Deferred<V>, var consumers: Int = 0)

    private val requests = mutableMapOf<K, Request<V>>()

    suspend fun await(key: K, fetch: suspend () -> V): V {
        val context = currentCoroutineContext()
        context.ensureActive()
        val request =
            synchronized(requests) {
                requests
                    .getOrPut(key) {
                        val owner = SupervisorJob()
                        val result =
                            CoroutineScope(context + owner).async(start = CoroutineStart.LAZY) {
                                fetch()
                            }
                        Request(owner, result)
                    }
                    .also { it.consumers++ }
            }
        try {
            context.ensureActive()
            request.result.start()
            return request.result.await()
        } finally {
            synchronized(requests) {
                request.consumers--
                if (request.consumers == 0) {
                    requests.remove(key)
                    request.owner.cancel()
                }
            }
        }
    }
}
