package io.github.ultrafeed.app4pda.core

/** Operational limits. UI dimensions and protocol identifiers remain with their own models. */
object AppPolicy {
    /** Check for a new App4PDA release at most once per day on foreground entry. */
    const val SELF_UPDATE_INTERVAL_MILLIS: Long = 24 * 60 * 60 * 1_000L

    /** Bounds resource draining and size recount during an explicit temporary-cache clear. */
    const val CACHE_CLEAR_TIMEOUT_MILLIS: Long = 30_000L

    // Ketch exposes asynchronous local commands; bound waiting for database acknowledgement.
    const val DOWNLOAD_COMMAND_TIMEOUT_MILLIS: Long = 10_000L

    /**
     * Settings and session backups are small; bound untrusted document reads before JSON decoding.
     */
    const val MAX_BACKUP_BYTES: Int = 10 * 1024 * 1024

    // Public API requires a supported client version; verified against OpenStore's protocol.
    const val RUSTORE_CLIENT_CODE: String = "1105003"
    const val RUSTORE_CLIENT_NAME: String = "1.107.0.3"

    /** Bound batch payloads; this is an application budget, not a published server limit. */
    const val RUSTORE_BATCH_SIZE: Int = 20

    /** Avoid bursts across metadata and delivery endpoints; the server quota is unknown. */
    const val RUSTORE_REQUEST_INTERVAL_MILLIS: Long = 1_000L

    /** Coalesce filesystem bursts without making completed files visibly slow to appear. */
    const val FILE_EVENT_DEBOUNCE_MILLIS: Long = 150L

    /** Package broadcasts commonly arrive in bursts during installation/replacement. */
    const val PACKAGE_EVENT_DEBOUNCE_MILLIS: Long = 200L

    /** Standard short grace period retains subscriptions across a configuration recreation. */
    const val SUBSCRIPTION_STOP_TIMEOUT_MILLIS: Long = 5_000L

    /** WorkManager does not support periodic work more often than once every fifteen minutes. */
    const val UPDATE_MIN_INTERVAL_MINUTES: Long = 15L

    /** A minute avoids immediate background retry loops; WorkManager increases later delays. */
    const val UPDATE_BACKOFF_MINUTES: Long = 1L

    /**
     * Bound page redirect chains to terminate loops while allowing multi-stage forum navigation.
     */
    const val PAGE_REDIRECT_LIMIT: Int = 20

    /** File probing needs a shorter chain; each hop must be checked before transfer enqueue. */
    const val FILE_REDIRECT_LIMIT: Int = 5

    /**
     * Allow slow mobile connection setup, but terminate an unreachable endpoint after 25 seconds.
     */
    const val HTTP_CONNECT_MILLIS: Long = 25_000L

    /**
     * Authorization may take longer than a page read; this is an app budget, not a server quota.
     */
    const val HTTP_AUTH_READ_MILLIS: Long = 30_000L

    /** Bound an idle forum page connection; interactive browser access runs independently. */
    const val HTTP_PAGE_READ_MILLIS: Long = 10_000L

    /**
     * Without a server deadline, wait a minute rather than immediately repeating a Play failure.
     */
    const val PLAY_RETRY_DEFAULT_MILLIS: Long = 60_000L

    /**
     * Cap an unreasonable Play retry deadline so a transient failure cannot disable checks forever.
     */
    const val PLAY_RETRY_MAX_MILLIS: Long = 24 * 60 * 60 * 1000L

    /**
     * 4PDA reports a 15-minute search quota; the requested 10-second margin avoids its boundary.
     */
    const val SEARCH_COOLDOWN_MILLIS: Long = 910_000L

    /** Pause all forum work after a challenge without a positive server retry deadline. */
    const val FORUM_VERIFICATION_COOLDOWN_MILLIS: Long = 5 * 60 * 1000L

    /**
     * RuStore may omit Retry-After. Use the same conservative 15-minute safety window as forum
     * search instead of retrying on every launch; this is not a claimed RuStore quota duration.
     */
    const val RUSTORE_RETRY_DEFAULT_MILLIS: Long = SEARCH_COOLDOWN_MILLIS

    /**
     * User policy: temporary content is retained for at most seven days. Never applies to downloads
     * or credentials.
     */
    const val CACHE_TTL_MILLIS: Long = 7 * 24 * 60 * 60 * 1000L

    /** Agreed adaptive request pacing: one request per second initially. */
    const val REQUEST_INITIAL_MILLIS: Long = 1000L

    /** Adjustment step; the initial interval is also the minimum pause after a response. */
    const val REQUEST_STEP_MILLIS: Long = 500L

    /** Agreed maximum pacing delay after transient server failures. */
    const val REQUEST_MAX_MILLIS: Long = 30_000L

    /** Ten consecutive successful requests are required before increasing the rate. */
    const val REQUEST_SUCCESS_COUNT: Int = 10

    /** Small cooperative wait keeps an interactive page ahead of paced background work. */
    const val REQUEST_PRIORITY_POLL_MILLIS: Long = 25L

    /** Preserve the total topic-loading budget, including scheduler pacing and redirects. */
    const val DETAILS_FORUM_TIMEOUT_MILLIS: Long = 185_000L

    /** Existing bounded Play lookup; independent failure must not keep the forum card loading. */
    const val DETAILS_PLAY_TIMEOUT_MILLIS: Long = 35_000L

    /** Bound one background topic metadata check, including scheduler waiting. */
    const val BINDING_TOPIC_TIMEOUT_MILLIS: Long = 60_000L

    /** Refresh metadata of an established forum link daily without changing its association. */
    const val LINKED_TOPIC_INTERVAL_MILLIS: Long = 24 * 60 * 60 * 1000L

    /**
     * Reuse Play metadata for an hour to avoid repeating its lookup on every foreground refresh.
     */
    const val PLAY_CHECK_INTERVAL_MILLIS: Long = 60 * 60 * 1000L

    /** Coalesce overlapping foreground/Worker digest metadata checks within one hour. */
    const val DIGEST_METADATA_INTERVAL_MILLIS: Long = 60 * 60 * 1000L
}
