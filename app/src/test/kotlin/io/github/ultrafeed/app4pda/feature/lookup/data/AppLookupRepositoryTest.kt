package io.github.ultrafeed.app4pda.feature.lookup.data

import io.github.ultrafeed.app4pda.data.forum.ForumInfo
import io.github.ultrafeed.app4pda.data.forum.ForumPackageLookup
import io.github.ultrafeed.app4pda.data.forum.ForumTopicHeader
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert.fail
import org.junit.Test

class AppLookupRepositoryTest {
    @Test
    fun mapsOnlyAConfirmedSourceMatch(): Unit = runTest {
        val source = ForumPackageLookup { pack ->
            if (pack == "org.example")
                ForumInfo(
                    ForumTopicHeader(
                        42,
                        "Model",
                        listOf(
                            pack,
                        ),
                        "",
                        "1",
                        "",
                        "",
                        0,
                        0,
                    ),
                    emptyList(),
                    "",
                    emptyList(),
                )
            else null
        }
        val repository =
            AppLookupRepository(
                source,
            )
        MatcherAssert.assertThat(
            repository.find("org.example"),
            CoreMatchers.`is`(
                AppLookupMatch(
                    42,
                    "org.example",
                ),
            ),
        )
        MatcherAssert.assertThat(
            repository.find("Model"),
            CoreMatchers.nullValue(),
        )
    }

    @Test
    fun sourceFailureIsNotAnEmptySuccessfulLookup(): Unit = runTest {
        try {
            AppLookupRepository {
                    throw IOException(
                        "Offline",
                    )
                }
                .find(
                    "org.example",
                )
            fail()
        } catch (_: IOException) {}
    }
}
