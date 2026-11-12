package io.github.ultrafeed.app4pda.core.formatting

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun comparesNumericSegmentsAndMissingZeroes() {
        assertThat(
            VersionComparator.compare("1.9", "1.10"),
            `is`(
                2,
            ),
        )
        assertThat(
            VersionComparator.compare("2.0", "1.9"),
            `is`(
                1,
            ),
        )
        assertThat(
            VersionComparator.compare("1", "1.0.0"),
            `is`(
                0,
            ),
        )
    }

    @Test
    fun prereleasesAndSegmentLimitKeepForumSemantics() {
        assertThat(
            VersionComparator.compare("1.0 alpha", "1.0 beta"),
            `is`(
                2,
            ),
        )
        assertThat(
            VersionComparator.compare("1.0 beta", "1.0"),
            `is`(
                2,
            ),
        )
        assertThat(
            VersionComparator.compare("1.1", "1.2"),
            `is`(
                2,
            ),
        )
        assertThat(
            VersionComparator.compare("1.0 alpha", "1.0 beta"),
            `is`(
                2,
            ),
        )
    }

    @Test
    fun equivalentAlternativeTakesPrecedenceOverOtherReleases() {
        assertThat(
            VersionComparator.compare("1.0", "2.0|1.0"),
            `is`(
                0,
            ),
        )
        assertThat(
            VersionComparator.compare("1.0", "0.9/2.0"),
            `is`(
                2,
            ),
        )
        assertThat(
            VersionComparator.compare("1.0", "0.9\\2.0"),
            `is`(
                2,
            ),
        )
    }

    @Test
    fun absentOrUnparseableVersionIsUnknown() {
        assertThat(
            VersionComparator.compare(null, "2.0"),
            `is`(
                0,
            ),
        )
        assertThat(
            VersionComparator.compare("name", "2.0"),
            `is`(
                0,
            ),
        )
        assertThat(
            VersionComparator.compare("1.0", ""),
            `is`(
                0,
            ),
        )
    }
}
