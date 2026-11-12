package io.github.ultrafeed.app4pda.data.rustore

import kotlinx.serialization.Serializable

/** A version check is not a full store page and contains no version name. */
@Serializable internal data class RuStoreListing(val packageName: String, val versionCode: Long)

@Serializable internal data class RuStoreBatchRequest(val packageNames: List<String>)

internal data class RuStoreBatch(
    val found: Map<String, RuStoreListing>,
    val unresolved: Set<String>,
)
