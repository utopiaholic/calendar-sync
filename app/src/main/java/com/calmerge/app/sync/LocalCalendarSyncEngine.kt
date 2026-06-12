package com.calmerge.app.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.CalendarSourceEntity
import com.calmerge.app.data.db.EventInstanceEntity

/**
 * Fetches events from the device's CalendarContract for LOCAL accounts.
 * Each included CalendarSourceEntity maps to one device calendar by
 * providerCalendarId (the CalendarContract _ID as a string).
 */
class LocalCalendarSyncEngine(
    private val context: Context,
    private val store: LocalCalendarStore,
) : CalendarSourceEngine {

    override val accountType: AccountType = AccountType.LOCAL

    override suspend fun fetch(
        account: AccountEntity,
        includedSources: List<CalendarSourceEntity>,
        window: SyncWindow,
    ): Map<String, List<EventInstanceEntity>> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw CalendarPermissionException("Calendar permission revoked")
        }
        return includedSources.associate { source ->
            val calendarId = source.providerCalendarId.toLongOrNull()
                ?: error("Invalid calendar id for source ${source.id}: '${source.providerCalendarId}'")
            source.id to store.queryEvents(calendarId, source.id, window)
        }
    }
}
