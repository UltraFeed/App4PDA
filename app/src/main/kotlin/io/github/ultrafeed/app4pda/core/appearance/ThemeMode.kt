package io.github.ultrafeed.app4pda.core.appearance

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemDark
            LIGHT -> false
            DARK -> true
        }

    companion object {
        fun parse(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/** Default seed for the application's custom accent. */
object AppearanceDefaults {
    const val ACCENT: Int = 0xff1b6ef3.toInt()
}
