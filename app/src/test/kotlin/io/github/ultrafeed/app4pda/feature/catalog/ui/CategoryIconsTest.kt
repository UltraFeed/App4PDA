package io.github.ultrafeed.app4pda.feature.catalog.ui

import androidx.compose.ui.unit.dp
import io.github.ultrafeed.app4pda.data.forum.SectionIcon
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class CategoryIconsTest {
    @Test
    fun everyCatalogCategoryHasUsableMaterialArtwork() {
        SectionIcon.entries.forEach { kind ->
            val icon = kind.materialIcon()
            assertThat(kind.name, icon.defaultWidth > 0.dp && icon.defaultHeight > 0.dp, `is`(true))
        }
    }
}
