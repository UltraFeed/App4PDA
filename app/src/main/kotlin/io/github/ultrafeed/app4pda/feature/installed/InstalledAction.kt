package io.github.ultrafeed.app4pda.feature.installed

/** Screen actions; names are not a persistence or navigation protocol. */
enum class InstalledAction {
    RUN,
    SYSTEM_INFO,
    UNINSTALL,
    HIDE,
    IGNORE_FORUM,
    IGNORE_MARKET,
    IGNORE_RUSTORE;

    fun isAvailableFor(app: InstalledApp): Boolean =
        when (this) {
            HIDE -> app.hidden || app.userChoicesEditable
            RUN,
            SYSTEM_INFO -> !app.hidden
            else -> app.userChoicesEditable
        }
}
