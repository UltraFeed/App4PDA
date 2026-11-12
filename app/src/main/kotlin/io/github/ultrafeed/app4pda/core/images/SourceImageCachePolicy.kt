package io.github.ultrafeed.app4pda.core.images

import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options

/** Recheck the switch when the response arrives, not only when its request was created. */
@OptIn(ExperimentalCoilApi::class)
internal class SourceImageCachePolicy(private val enabled: suspend () -> Boolean) : CacheStrategy {
    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult =
        if (enabled()) CacheStrategy.DEFAULT.read(cacheResponse, networkRequest, options)
        else CacheStrategy.ReadResult(networkRequest)

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult =
        if (enabled())
            CacheStrategy.DEFAULT.write(
                cacheResponse,
                networkRequest,
                networkResponse,
                options,
            )
        else CacheStrategy.WriteResult.DISABLED
}
