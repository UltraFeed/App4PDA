package io.github.ultrafeed.app4pda.feature.catalog

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class CatalogQueryTest {
    @Test
    fun queryPreservesExactPackageAndSearchIndependently() {
        val query = CatalogQuery(linkedPackage = "org.example", search = "Example")
        assertThat(
            query.linkedPackage,
            `is`(
                "org.example",
            ),
        )
        assertThat(
            query.search,
            `is`(
                "Example",
            ),
        )
        assertThat(
            query.topicId,
            `is`(
                0,
            ),
        )
    }
}
