package com.calmerge.app.sync

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * FR-6: rolling sync window, default 7 days past to 60 days future.
 * Boundaries are computed in UTC from "now".
 */
data class SyncWindow(val startUtc: Instant, val endUtc: Instant) {
    companion object {
        const val DEFAULT_DAYS_PAST = 7L
        const val DEFAULT_DAYS_FUTURE = 60L

        fun around(
            now: Instant,
            daysPast: Long = DEFAULT_DAYS_PAST,
            daysFuture: Long = DEFAULT_DAYS_FUTURE,
        ): SyncWindow = SyncWindow(
            startUtc = now.truncatedTo(ChronoUnit.DAYS).minus(daysPast, ChronoUnit.DAYS),
            endUtc = now.truncatedTo(ChronoUnit.DAYS).plus(daysFuture + 1, ChronoUnit.DAYS),
        )
    }

    /** ISO-8601 instant strings as expected by both Graph and Google query params. */
    val startIso: String get() = startUtc.toString()
    val endIso: String get() = endUtc.toString()
}
