package io.github.ultrafeed.app4pda.feature.recent.data

import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreview
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class DigestCoverageTest {
    private val issues =
        listOf(
                "26 октября 2026 - 02 ноября 2026",
                "19 октября 2026 - 26 октября 2026",
                "28 сентября 2026 - 05 октября 2026",
            )
            .map {
                DigestIssue(it, DigestKind.entries.toSet())
            }

    private fun snapshot(): DigestSnapshot =
        DigestSnapshot(
            issues = issues,
            groups =
                issues.flatMapIndexed { index, issue ->
                    DigestKind.entries.map { kind ->
                        DigestGroup(
                            issue.period,
                            "Category",
                            kind,
                            listOf(DigestEntry(index + 1, "App", "Description")),
                        )
                    }
                },
        )

    @Test
    fun defaultsSelectTheLatestPublishedIssueForBothKinds() {
        val selected =
            DigestCoverage()
                .select(
                    snapshot()
                        .copy(
                            issues =
                                issues.reversed() +
                                    issues
                                        .first()
                                        .copy(period = "26\u00a0октября 2026 — 02 ноября 2026"),
                            groups = snapshot().groups.reversed(),
                        ),
                )
        assertThat(
            selected.groups.map { it.period }.toSet(),
            `is`(
                setOf(
                    issues.first().period,
                ),
            ),
        )
        assertThat(
            selected.issues.size,
            `is`(
                3,
            ),
        )
        assertThat(
            selected.groups.map { it.kind }.toSet(),
            `is`(
                DigestKind.entries.toSet(),
            ),
        )
    }

    @Test
    fun independentCountsDoNotManufactureMissingIssues() {
        val coverage = DigestCoverage(newIssues = 2, updatedIssues = 6)
        val selected = coverage.select(snapshot())
        assertThat(
            selected.groups.forDisplay(DigestKind.NEW).map { it.period },
            `is`(
                issues
                    .take(
                        2,
                    )
                    .map { it.period },
            ),
        )
        assertThat(
            selected.groups.forDisplay(DigestKind.UPDATED).map { it.period },
            `is`(
                issues.map { it.period },
            ),
        )
        assertThat(
            coverage.updatedIssues,
            `is`(
                6,
            ),
        )
    }

    @Test
    fun emptyRecognizedSectionCountsAsAnIssueWithoutBorrowingOlderEntries() {
        val emptyLatest =
            snapshot()
                .copy(
                    groups =
                        snapshot().groups.filterNot {
                            it.period == issues.first().period && it.kind == DigestKind.NEW
                        },
                )
        DigestCoverage().requireComplete(emptyLatest.issues)
        assertThat(
            DigestCoverage().select(emptyLatest).groups.forDisplay(DigestKind.NEW).isEmpty(),
            `is`(true),
        )
        assertThat(
            DigestCoverage().select(emptyLatest).issues.first(),
            `is`(
                issues.first(),
            ),
        )
    }

    @Test
    fun unrecognizedSelectedSectionFailsBeforeReplacingSuccessfulContent() {
        val broken = issues.mapIndexed { index, issue ->
            if (index == 0) issue.copy(sections = setOf(DigestKind.NEW)) else issue
        }
        assertThrows(DigestContentException::class.java) {
            DigestCoverage().requireComplete(broken)
        }
    }

    @Test
    fun newPublicationAdvancesBothWindowsAndDropsUnselectedMetadata() {
        val source =
            snapshot()
                .copy(
                    issues =
                        listOf(
                            DigestIssue(
                                "02 ноября 2026 - 09 ноября 2026",
                                DigestKind.entries.toSet(),
                            ),
                        ) + issues,
                    previews =
                        mapOf(
                            1 to
                                ForumTopicPreview(
                                    null,
                                    false,
                                ),
                        ),
                )
        val selected = DigestCoverage().select(source)
        assertThat(selected.groups.isEmpty(), `is`(true))
        assertThat(selected.previews.isEmpty(), `is`(true))
    }

    @Test
    fun repeatedTopicsRemainOnlyInTheNewestIntervalOfEachKind() {
        val original = snapshot()
        val repeated =
            original.copy(
                groups =
                    original.groups.map {
                        it.copy(entries = listOf(DigestEntry(1, "App", "Description")))
                    } +
                        original.groups
                            .first()
                            .copy(
                                category = "Another category",
                                entries = listOf(DigestEntry(1, "App", "Description")),
                            ),
            )
        val selected = DigestCoverage(6, 6).select(repeated)
        assertThat(
            selected.groups.size,
            `is`(
                2,
            ),
        )
        assertThat(
            selected.groups.map { it.period }.toSet(),
            `is`(
                setOf(
                    issues.first().period,
                ),
            ),
        )
        assertThat(
            selected.issues.size,
            `is`(
                3,
            ),
        )
        assertThat(
            repeated.groups.size,
            `is`(
                7,
            ),
        )
    }

    @Test
    fun nonPositiveCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { DigestCoverage(newIssues = 0) }
        assertThrows(IllegalArgumentException::class.java) { DigestCoverage(updatedIssues = -1) }
        assertThrows(IllegalArgumentException::class.java) { DigestCoverage(updatedIssues = 7) }
        assertThrows(DigestContentException::class.java) {
            DigestCoverage()
                .select(
                    snapshot()
                        .copy(
                            issues =
                                listOf(
                                    DigestIssue(
                                        "31 февраля 2026 - 02 марта 2026",
                                        DigestKind.entries.toSet(),
                                    ),
                                ),
                        ),
                )
        }
    }
}
