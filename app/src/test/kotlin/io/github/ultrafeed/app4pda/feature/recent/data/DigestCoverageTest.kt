package io.github.ultrafeed.app4pda.feature.recent.data

import io.github.ultrafeed.app4pda.data.forum.ForumTopicPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
        assertEquals(setOf(issues.first().period), selected.groups.map { it.period }.toSet())
        assertEquals(3, selected.issues.size)
        assertEquals(DigestKind.entries.toSet(), selected.groups.map { it.kind }.toSet())
    }

    @Test
    fun independentCountsDoNotManufactureMissingIssues() {
        val coverage = DigestCoverage(newIssues = 2, updatedIssues = 6)
        val selected = coverage.select(snapshot())
        assertEquals(
            issues.take(2).map { it.period },
            selected.groups.forDisplay(DigestKind.NEW).map { it.period },
        )
        assertEquals(
            issues.map { it.period },
            selected.groups.forDisplay(DigestKind.UPDATED).map { it.period },
        )
        assertEquals(6, coverage.updatedIssues)
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
        assertTrue(DigestCoverage().select(emptyLatest).groups.forDisplay(DigestKind.NEW).isEmpty())
        assertEquals(issues.first(), DigestCoverage().select(emptyLatest).issues.first())
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
                                    "",
                                    "",
                                    0,
                                ),
                        ),
                )
        val selected = DigestCoverage().select(source)
        assertTrue(selected.groups.isEmpty())
        assertTrue(selected.previews.isEmpty())
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
        assertEquals(2, selected.groups.size)
        assertEquals(setOf(issues.first().period), selected.groups.map { it.period }.toSet())
        assertEquals(3, selected.issues.size)
        assertEquals(7, repeated.groups.size)
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
