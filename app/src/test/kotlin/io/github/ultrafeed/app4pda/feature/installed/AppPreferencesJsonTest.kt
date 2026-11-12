package io.github.ultrafeed.app4pda.feature.installed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AppPreferencesJsonTest {
    @Test
    fun defaultsAreOmittedAndEveryModelFieldRoundTrips() {
        assertThat(AppPreferencesJson.differences(AppPreferences()).isEmpty(), `is`(true))
        val complete = Json { encodeDefaults = true }
        val defaults =
            complete.encodeToJsonElement(
                AppPreferences.serializer(),
                AppPreferences(),
            ) as JsonObject
        for (key in defaults.keys) {
            val patch = buildJsonObject { put(key, true) }
            val value = AppPreferencesJson.merge(AppPreferences(), patch)
            assertThat(
                AppPreferencesJson.differences(value),
                `is`(
                    patch,
                ),
            )
            assertThat(
                AppPreferencesJson.knownFields(patch),
                `is`(
                    patch,
                ),
            )
        }
    }

    @Test
    fun absentFieldsArePreservedAndDeletedFieldsAreIgnored() {
        val before = AppPreferences(favorite = true, hidden = true)
        val patch = buildJsonObject {
            put("ignoreForum", true)
            put("visibility", "AUTO")
        }
        assertThat(
            AppPreferencesJson.merge(before, patch),
            `is`(
                before.copy(
                    ignoreForum = true,
                ),
            ),
        )
        assertThat(
            AppPreferencesJson.knownFields(patch),
            `is`(
                buildJsonObject {
                    put(
                        "ignoreForum",
                        true,
                    )
                },
            ),
        )
        val unhide = buildJsonObject { put("hidden", false) }
        assertThat(
            AppPreferencesJson.merge(before, unhide),
            `is`(
                before.copy(
                    hidden = false,
                ),
            ),
        )
    }

    @Test
    fun knownFieldsRejectWrongTypesBeforeAnyWrite() {
        for (value in
            listOf(
                JsonPrimitive("true"),
                JsonPrimitive(1),
                JsonNull,
                JsonObject(emptyMap()),
            )) {
            assertThrows(IllegalArgumentException::class.java) {
                AppPreferencesJson.knownFields(buildJsonObject { put("hidden", value) })
            }
        }
    }
}
