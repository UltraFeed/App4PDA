package io.github.ultrafeed.app4pda.feature.recent.data

import kotlinx.serialization.Serializable

/** A published period exists even if one of its recognized sections has no entries. */
@Serializable
data class DigestIssue(val period: String, val sections: Set<DigestKind>) {
    internal val key: String
        get() = DigestPeriod.parse(period).let { "${it.start}/${it.end}" }
}

data class DigestCoverage(val newIssues: Int = 1, val updatedIssues: Int = 1) {
    init {
        require(newIssues in ISSUE_COUNTS && updatedIssues in ISSUE_COUNTS)
    }

    fun requested(kind: DigestKind): Int = if (kind == DigestKind.NEW) newIssues else updatedIssues

    fun select(snapshot: DigestSnapshot): DigestSnapshot {
        val issues = orderedIssues(snapshot.issues)
        val labels = issues.associate { DigestPeriod.parse(it.period) to it.period }
        val sourceLabels =
            snapshot.issues.associate {
                it.period to labels.getValue(DigestPeriod.parse(it.period))
            }
        val source =
            snapshot.groups.map { group ->
                group.copy(
                    period =
                        sourceLabels[group.period]
                            ?: throw DigestContentException(
                                "Digest group without a published period",
                            ),
                )
            }
        val groups =
            DigestKind.entries.flatMap { kind ->
                selectGroups(source, issues.take(requested(kind)), kind)
            }
        val topics = groups.flatMap { it.entries }.map { it.topicId }.toSet()
        return snapshot.copy(
            groups = groups,
            issues = issues,
            previews = snapshot.previews.filterKeys { it in topics },
        )
    }

    fun requireComplete(issues: List<DigestIssue>) {
        if (issues.isEmpty()) throw DigestContentException("Digest contains no recognized issues")
        val normalized = orderedIssues(issues)
        DigestKind.entries.forEach { kind ->
            if (normalized.any { kind !in it.sections }) {
                throw DigestContentException("Digest section $kind not recognized")
            }
        }
    }

    companion object {
        val ISSUE_COUNTS: IntRange = 1..6
    }
}

internal fun orderedIssues(issues: List<DigestIssue>): List<DigestIssue> =
    issues
        .groupBy { DigestPeriod.parse(it.period) }
        .toList()
        .sortedWith(
            compareByDescending<Pair<DigestPeriod, List<DigestIssue>>> { it.first.end }
                .thenByDescending { it.first.start },
        )
        .map { (_, matches) ->
            DigestIssue(matches.first().period, matches.flatMap { it.sections }.toSet())
        }

internal fun selectGroups(
    source: List<DigestGroup>,
    issues: List<DigestIssue>,
    kind: DigestKind,
): List<DigestGroup> {
    val seen = mutableSetOf<Int>()
    return issues.flatMap { issue ->
        source
            .filter { it.kind == kind && it.period == issue.period }
            .groupBy { it.category }
            .mapNotNull { (_, matches) ->
                val entries = matches.flatMap { it.entries }.filter { seen.add(it.topicId) }
                matches.first().copy(entries = entries).takeIf { entries.isNotEmpty() }
            }
    }
}
