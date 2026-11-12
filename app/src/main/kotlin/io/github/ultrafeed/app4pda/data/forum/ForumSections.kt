package io.github.ultrafeed.app4pda.data.forum

import io.github.ultrafeed.app4pda.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Product scope, independent of the forum's mutable list of search-form options. */
@Serializable
data class ForumSection(
    val id: Int,
    val parent: Int,
    @Transient val titleResource: Int = 0,
    val icon: SectionIcon,
    val forums: Set<Int> = setOf(id),
    val keyword: String? = null,
    val androidOnly: Boolean = false,
    val name: String = "",
)

@Serializable
enum class SectionIcon {
    APPS,
    GAMES,
    EMULATORS,
    SECURITY,
    CHILDREN,
    INTERNET,
    INTERFACE,
    PHONE,
    MEDIA,
    AUDIO,
    NEWS,
    CHAT,
    OFFICE,
    BOOK,
    SYSTEM,
    HEALTH,
    SHOP,
    TRAVEL,
    FINANCE,
    DEVICES,
    ARCHIVE,
    COMPUTER,
    ACTION,
    ARCADE,
    RACING,
    CASUAL,
    ADVENTURE,
    PUZZLE,
    BOARD,
    SIMULATION,
    SPORTS,
    STRATEGY,
    OTHER,
    UPCOMING,
}

object ForumSections {
    const val APPLICATIONS: Int = 212

    fun titleResource(id: Int): Int? =
        all.firstOrNull { it.id == id }?.titleResource?.takeIf { it != 0 }

    val all: List<ForumSection> =
        listOf(
            ForumSection(APPLICATIONS, 0, R.string.catalog_section_212, SectionIcon.APPS),
            ForumSection(320, 212, R.string.catalog_section_320, SectionIcon.SECURITY),
            ForumSection(794, 212, R.string.catalog_section_794, SectionIcon.CHILDREN),
            ForumSection(285, 212, R.string.catalog_section_285, SectionIcon.INTERNET),
            ForumSection(509, 212, R.string.catalog_section_509, SectionIcon.INTERFACE),
            ForumSection(315, 212, R.string.catalog_section_315, SectionIcon.PHONE),
            ForumSection(319, 212, R.string.catalog_section_319, SectionIcon.MEDIA),
            ForumSection(831, 212, R.string.catalog_section_831, SectionIcon.AUDIO),
            ForumSection(1144, 212, R.string.catalog_section_1144, SectionIcon.NEWS),
            ForumSection(811, 212, R.string.catalog_section_811, SectionIcon.CHAT),
            ForumSection(316, 212, R.string.catalog_section_316, SectionIcon.OFFICE),
            ForumSection(832, 212, R.string.catalog_section_832, SectionIcon.MEDIA),
            ForumSection(1131, 212, R.string.catalog_section_1131, SectionIcon.BOOK),
            ForumSection(317, 212, R.string.catalog_section_317, SectionIcon.SYSTEM),
            ForumSection(634, 212, R.string.catalog_section_634, SectionIcon.HEALTH),
            ForumSection(1129, 212, R.string.catalog_section_1129, SectionIcon.SHOP),
            ForumSection(751, 212, R.string.catalog_section_751, SectionIcon.TRAVEL),
            ForumSection(1130, 212, R.string.catalog_section_1130, SectionIcon.FINANCE),
            ForumSection(318, 212, R.string.catalog_section_318, SectionIcon.BOOK),
            ForumSection(1157, 212, R.string.catalog_section_1157, SectionIcon.DEVICES),
            ForumSection(321, 212, R.string.catalog_section_321, SectionIcon.OTHER),
            ForumSection(558, 212, R.string.catalog_section_558, SectionIcon.COMPUTER),
            ForumSection(1109, 212, R.string.catalog_section_1109, SectionIcon.ARCHIVE),
            ForumSection(
                413,
                212,
                R.string.catalog_section_413,
                SectionIcon.INTERFACE,
                setOf(315, 316, 317, 318, 319, 285, 320, 321, 509),
                "widg",
            ),
            ForumSection(
                506,
                212,
                R.string.catalog_section_506,
                SectionIcon.DEVICES,
                setOf(315, 316, 317, 318, 319, 285, 320, 321, 509),
                "OEM",
            ),
            ForumSection(
                286,
                212,
                R.string.catalog_section_286,
                SectionIcon.TRAVEL,
                setOf(237),
                "Android",
                true,
            ),
            ForumSection(
                1248,
                212,
                R.string.catalog_section_1248,
                SectionIcon.SYSTEM,
                setOf(1241),
                androidOnly = true,
            ),
            ForumSection(213, 0, R.string.catalog_section_213, SectionIcon.GAMES),
            ForumSection(1164, 213, R.string.catalog_section_1164, SectionIcon.UPCOMING),
            ForumSection(519, 213, R.string.catalog_section_519, SectionIcon.ACTION),
            ForumSection(520, 213, R.string.catalog_section_520, SectionIcon.ARCADE),
            ForumSection(521, 213, R.string.catalog_section_521, SectionIcon.RACING),
            ForumSection(522, 213, R.string.catalog_section_522, SectionIcon.CASUAL),
            ForumSection(523, 213, R.string.catalog_section_523, SectionIcon.ADVENTURE),
            ForumSection(524, 213, R.string.catalog_section_524, SectionIcon.PUZZLE),
            ForumSection(525, 213, R.string.catalog_section_525, SectionIcon.BOARD),
            ForumSection(526, 213, R.string.catalog_section_526, SectionIcon.SIMULATION),
            ForumSection(527, 213, R.string.catalog_section_527, SectionIcon.SPORTS),
            ForumSection(528, 213, R.string.catalog_section_528, SectionIcon.STRATEGY),
            ForumSection(812, 213, R.string.catalog_section_812, SectionIcon.CHILDREN),
            ForumSection(530, 213, R.string.catalog_section_530, SectionIcon.OTHER),
            ForumSection(1149, 213, R.string.catalog_section_1149, SectionIcon.ARCHIVE),
            ForumSection(529, 0, R.string.catalog_section_529, SectionIcon.EMULATORS),
            ForumSection(539, 529, R.string.catalog_section_539, SectionIcon.GAMES),
            ForumSection(540, 529, R.string.catalog_section_540, SectionIcon.COMPUTER),
        )
    val ids: Set<Int> = all.flatMapTo(linkedSetOf()) { it.forums }
    val androidOnlyIds: Set<Int> =
        all.filter { it.androidOnly }.flatMapTo(linkedSetOf()) { it.forums }

    fun section(id: Int): ForumSection = all.first { it.id == id }

    fun searchQuery(query: String, id: Int): String {
        val keyword = all.firstOrNull { it.id == id }?.keyword ?: return query
        return if (query.isBlank()) keyword else "+$keyword +${query.trim()}"
    }

    fun scope(section: Int): Set<Int> {
        require(section == 0 || all.any { it.id == section }) { "Unsupported forum section" }
        if (section == 0) return ids
        return section(section).forums.toMutableSet().apply {
            all.filter { it.parent == section }.forEach { addAll(scope(it.id)) }
        }
    }

    fun children(parent: Int): List<ForumSection> = all.filter { it.parent == parent }
}
