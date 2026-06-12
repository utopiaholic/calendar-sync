package com.calmerge.app.sync

import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.CalendarSourceEntity
import com.calmerge.app.data.db.EventInstanceEntity

/**
 * Read-only provider interface. Implementations fetch events from one source type
 * (ICS feed, device CalendarContract, future OAuth) and return them — they never
 * touch Room. All persistence is the coordinator's responsibility.
 *
 * To add a new calendar source: implement this interface, add the AccountType value,
 * and register the engine in CalMergeApp. No other changes needed.
 */
interface CalendarSourceEngine {

    val accountType: AccountType

    /**
     * Fetch events for every included source of this account within [window].
     * Returns a map of sourceId → events; sources absent from the map are treated
     * as having returned an empty list (no existing events are cleared for them).
     *
     * Throw [CalendarPermissionException] if the user has revoked the required
     * permission. All other exceptions propagate to the coordinator for ERROR status.
     */
    suspend fun fetch(
        account: AccountEntity,
        includedSources: List<CalendarSourceEntity>,
        window: SyncWindow,
    ): Map<String, List<EventInstanceEntity>>
}

/** Signals that a required Android permission was revoked by the user. */
class CalendarPermissionException(message: String) : Exception(message)
