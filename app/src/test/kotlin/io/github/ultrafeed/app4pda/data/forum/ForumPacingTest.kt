package io.github.ultrafeed.app4pda.data.forum

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ForumPacingTest {
    @Test
    fun successfulSeriesNeverDropsBelowOneSecond() {
        val pacing = ForumPacing()
        repeat(9) { pacing.succeeded() }
        assertThat(
            pacing.intervalMillis,
            `is`(
                1000,
            ),
        )
        pacing.succeeded()
        assertThat(
            pacing.intervalMillis,
            `is`(
                1000,
            ),
        )
        repeat(100) { pacing.succeeded() }
        assertThat(
            pacing.intervalMillis,
            `is`(
                1000,
            ),
        )
    }

    @Test
    fun failureSlowsDownAndBreaksTheSuccessSeries() {
        val pacing = ForumPacing()
        repeat(9) { pacing.succeeded() }
        pacing.failed()
        pacing.succeeded()
        assertThat(
            pacing.intervalMillis,
            `is`(
                1500,
            ),
        )
        repeat(100) { pacing.failed() }
        assertThat(
            pacing.intervalMillis,
            `is`(
                30000,
            ),
        )
    }
}
