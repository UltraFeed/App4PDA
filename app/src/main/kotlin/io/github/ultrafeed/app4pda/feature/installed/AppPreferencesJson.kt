package io.github.ultrafeed.app4pda.feature.installed

import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/** The user preference model owns both persisted fields and backup defaults. */
internal object AppPreferencesJson {
    private val serializer = serializer<AppPreferences>()
    private val complete = Json { encodeDefaults = true }

    fun differences(value: AppPreferences): JsonObject =
        Json.encodeToJsonElement(serializer, value) as JsonObject

    fun knownFields(value: JsonObject): JsonObject =
        JsonObject(
                value.filterKeys {
                    serializer.descriptor.getElementIndex(it) != CompositeDecoder.UNKNOWN_NAME
                },
            )
            .also { merge(AppPreferences(), it) }

    fun merge(current: AppPreferences, changes: JsonObject): AppPreferences {
        val present = changes.filterKeys {
            serializer.descriptor.getElementIndex(it) != CompositeDecoder.UNKNOWN_NAME
        }
        val original = complete.encodeToJsonElement(serializer, current) as JsonObject
        val merged = complete.decodeFromJsonElement(serializer, JsonObject(original + present))
        val canonical = complete.encodeToJsonElement(serializer, merged) as JsonObject
        require(present.all { (key, value) -> canonical[key] == value }) {
            "Invalid application preference type"
        }
        return merged
    }
}
