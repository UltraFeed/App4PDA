package io.github.ultrafeed.app4pda.core.formatting

/** Numeric and prerelease comparison used by forum versions, including alternative releases. */
object VersionComparator {
    private const val EQUAL_OR_UNKNOWN = 0
    const val INSTALLED_NEWER: Int = 1
    const val AVAILABLE_NEWER: Int = 2

    @JvmStatic
    fun compare(installed: String?, available: String?): Int {
        val local = installed?.let(::segments).orEmpty()
        if (local.isEmpty() || available.isNullOrEmpty()) return EQUAL_OR_UNKNOWN
        val comparisons =
            available
                .split('|', '/', '\\')
                .map(::segments)
                .filter { it.isNotEmpty() }
                .map { compareSegments(local, it) }
        return when {
            EQUAL_OR_UNKNOWN in comparisons -> EQUAL_OR_UNKNOWN
            AVAILABLE_NEWER in comparisons -> AVAILABLE_NEWER
            else -> comparisons.firstOrNull() ?: EQUAL_OR_UNKNOWN
        }
    }

    private fun compareSegments(local: List<Int>, remote: List<Int>): Int {
        val different =
            (0 until maxOf(local.size, remote.size)).firstOrNull {
                local.getOrElse(it) { 0 } != remote.getOrElse(it) { 0 }
            }
        return when {
            different == null -> EQUAL_OR_UNKNOWN
            local.getOrElse(different) { 0 } < remote.getOrElse(different) { 0 } -> AVAILABLE_NEWER
            else -> INSTALLED_NEWER
        }
    }

    private fun segments(version: String): List<Int> {
        val normalized =
            version
                .replace("-", " ")
                .replace(Regex("alpha", RegexOption.IGNORE_CASE), " -2 ")
                .replace(Regex("beta", RegexOption.IGNORE_CASE), " -1 ")
                .replace(Regex("[^0-9-]"), " ")
                .trim()
                .replace(Regex("\\s+"), ".")
        return Regex("(?<=\\.|^)([-\\d]+?)(?=\\.|$)")
            .findAll(normalized)
            .map { it.groupValues[1].toIntOrNull() ?: 0 }
            .toList()
    }
}
