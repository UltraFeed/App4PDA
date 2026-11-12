package io.github.ultrafeed.app4pda.feature.downloads.data

import android.database.SQLException
import android.util.Log
import io.github.ultrafeed.app4pda.feature.details.DetailsFile
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Controller-owned accepted work and one-shot outcomes survive screen destruction. */
class DownloadSubmissions
internal constructor(
    private val controller: DownloadController,
    private val locator: DownloadLocator,
    ioDispatcher: CoroutineDispatcher,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val results = Channel<Boolean>(Channel.UNLIMITED)
    val resultsFlow: Flow<Boolean> = results.receiveAsFlow()

    /** Accepted work belongs to the application, never to the screen that confirmed it. */
    fun submit(file: DetailsFile, pack: String) {
        submitOperation {
            val prepared = locator.prepare(file, pack)
            val store = prepared.filter { it.playFile != null }
            if (store.isNotEmpty()) {
                val first = store.first()
                controller.enqueuePlay(
                    first.playPackage,
                    first.playVersion,
                    store.map { checkNotNull(it.playFile) },
                    first.storeSource,
                )
            } else
                prepared.forEach {
                    controller.requestDownload(it.url, it.name, it.topicId, it.postId)
                }
        }
    }

    fun submitUrl(url: String, name: String, topic: Int = 0, post: Int = 0) {
        submitOperation { controller.requestDownload(url, name, topic, post) }
    }

    private fun submitOperation(operation: suspend () -> Unit) {
        scope.launch {
            try {
                operation()
                results.send(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IOException) {
                failed(failure)
            } catch (failure: IllegalStateException) {
                failed(failure)
            } catch (failure: IllegalArgumentException) {
                failed(failure)
            } catch (failure: SecurityException) {
                failed(failure)
            } catch (failure: SQLException) {
                failed(failure)
            }
        }
    }

    private fun failed(failure: Exception) {
        Log.e("Downloads", "Download submission failed", failure)
        results.trySend(false)
    }

    override fun close() {
        scope.cancel()
        results.close()
    }
}
