package io.github.ultrafeed.app4pda.feature.installed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesJsonTest {
    @Test
    fun defaultsAreOmittedAndEveryModelFieldRoundTrips() {
        assertTrue(AppPreferencesJson.differences(AppPreferences()).isEmpty())
        val complete = Json { encodeDefaults = true }
        val defaults =
            complete.encodeToJsonElement(AppPreferences.serializer(), AppPreferences())
                as JsonObject
        for (key in defaults.keys) {
            val patch = buildJsonObject { put(key, true) }
            val value = AppPreferencesJson.merge(AppPreferences(), patch)
            MatcherAssert.assertThat(
                AppPreferencesJson.differences(value),
                CoreMatchers.`is`(
                    patch,
                ),
            )
            MatcherAssert.assertThat(
                AppPreferencesJson.knownFields(patch),
                CoreMatchers.`is`(
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
        MatcherAssert.assertThat(
            AppPreferencesJson.merge(before, patch),
            CoreMatchers.`is`(
                before.copy(
                    ignoreForum = true,
                ),
            ),
        )
        MatcherAssert.assertThat(
            AppPreferencesJson.knownFields(patch),
            CoreMatchers.`is`(
                buildJsonObject {
                    put(
                        "ignoreForum",
                        true,
                    )
                },
            ),
        )
        val unhide = buildJsonObject { put("hidden", false) }
        MatcherAssert.assertThat(
            AppPreferencesJson.merge(before, unhide),
            CoreMatchers.`is`(
                before.copy(
                    hidden = false,
                ),
            ),
        )
    }

    @Test
    fun knownFieldsRejectWrongTypesBeforeAnyWrite() {
        for (value in
            listOf(JsonPrimitive("true"), JsonPrimitive(1), JsonNull, JsonObject(emptyMap()))) {
            assertThrows(IllegalArgumentException::class.java) {
                AppPreferencesJson.knownFields(buildJsonObject { put("hidden", value) })
            }
        }
    }
}
