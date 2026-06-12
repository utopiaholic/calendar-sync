package com.calmerge.app.sync

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import com.calmerge.app.data.db.EventInstanceEntity
import com.calmerge.app.data.db.ResponseStatus
import com.calmerge.app.data.db.ShowAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * The single ContentResolver touchpoint for CalendarContract in the app.
 * Both the sync engine and the calendar picker go through here; nothing
 * else may import CalendarContract directly.
 */
class LocalCalendarStore(private val context: Context) {

    suspend fun listCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE,
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DeviceCalendar(
                            id = cursor.getLong(0),
                            displayName = cursor.getString(1).orEmpty(),
                            accountName = cursor.getString(2).orEmpty(),
                            accountType = cursor.getString(3).orEmpty(),
                            visible = cursor.getInt(4) == 1,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    suspend fun queryEvents(
        calendarId: Long,
        calendarSourceId: String,
        window: SyncWindow,
    ): List<EventInstanceEntity> = withContext(Dispatchers.IO) {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, window.startUtc.toEpochMilli())
            ContentUris.appendId(it, window.endUtc.toEpochMilli())
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,       // 0
            CalendarContract.Instances.BEGIN,           // 1
            CalendarContract.Instances.END,             // 2
            CalendarContract.Instances.ALL_DAY,         // 3
            CalendarContract.Instances.TITLE,           // 4
            CalendarContract.Instances.DESCRIPTION,     // 5
            CalendarContract.Instances.EVENT_LOCATION,  // 6
            CalendarContract.Instances.AVAILABILITY,    // 7
            CalendarContract.Instances.SELF_ATTENDEE_STATUS, // 8
            CalendarContract.Instances.ORGANIZER,       // 9
            CalendarContract.Instances.UID_2445,        // 10
        )
        context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Instances.CALENDAR_ID} = ?",
            arrayOf(calendarId.toString()),
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val begin = cursor.getLong(1)
                    val end = cursor.getLong(2)
                    val allDay = cursor.getInt(3) == 1
                    add(
                        mapToEntity(
                            calendarSourceId = calendarSourceId,
                            eventId = eventId,
                            begin = begin,
                            end = end,
                            allDay = allDay,
                            title = cursor.getString(4),
                            description = cursor.getString(5),
                            location = cursor.getString(6),
                            availabilityInt = cursor.getInt(7),
                            attendeeStatusInt = cursor.getInt(8),
                            organizer = cursor.getString(9),
                            uid2445 = cursor.getString(10),
                        ),
                    )
                }
            }
        }.orEmpty()
    }
}

/**
 * Pure mapper — takes only primitives so it can be exercised in JVM unit tests
 * without android.jar stubs. The CalendarContract int values are mirrored as
 * local constants below.
 */
internal fun mapToEntity(
    calendarSourceId: String,
    eventId: Long,
    begin: Long,
    end: Long,
    allDay: Boolean,
    title: String?,
    description: String?,
    location: String?,
    availabilityInt: Int,
    attendeeStatusInt: Int,
    organizer: String?,
    uid2445: String?,
): EventInstanceEntity {
    // Instances pre-expands recurring events; EVENT_ID+BEGIN is the unique occurrence key.
    val providerEventId = "$eventId/$begin"

    val (startUtc, endUtc, startDate, endDate) = if (allDay) {
        // BEGIN/END for all-day events are UTC midnight timestamps.
        // Stored as ISO LocalDate strings (end exclusive) per FR-11.
        val startLocal = Instant.ofEpochMilli(begin).atOffset(ZoneOffset.UTC).toLocalDate()
        val endLocal = Instant.ofEpochMilli(end).atOffset(ZoneOffset.UTC).toLocalDate()
            .let { if (it == startLocal) it.plusDays(1) else it }
        AllDayFields(null, null, startLocal.format(ISO_DATE), endLocal.format(ISO_DATE))
    } else {
        AllDayFields(begin, end.takeIf { it > begin } ?: begin, null, null)
    }

    return EventInstanceEntity(
        id = UUID.nameUUIDFromBytes("$calendarSourceId/$providerEventId".toByteArray()).toString(),
        calendarSourceId = calendarSourceId,
        providerEventId = providerEventId,
        iCalUId = uid2445,
        title = title.orEmpty(),
        startUtc = startUtc,
        endUtc = endUtc,
        isAllDay = allDay,
        startDate = startDate,
        endDate = endDate,
        showAs = availabilityInt.toShowAs(),
        responseStatus = attendeeStatusInt.toResponseStatus(),
        location = location,
        organizer = organizer,
        lastModifiedUtc = null,
    )
}

private data class AllDayFields(
    val startUtc: Long?,
    val endUtc: Long?,
    val startDate: String?,
    val endDate: String?,
)

private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

// Mirror CalendarContract int values so the pure mapper has no android dependency.
internal const val AVAILABILITY_BUSY = 0
internal const val AVAILABILITY_FREE = 1
internal const val AVAILABILITY_TENTATIVE = 2

internal const val ATTENDEE_NONE = 0
internal const val ATTENDEE_ACCEPTED = 1
internal const val ATTENDEE_DECLINED = 2
internal const val ATTENDEE_INVITED = 3
internal const val ATTENDEE_TENTATIVE = 4

internal fun Int.toShowAs(): ShowAs = when (this) {
    AVAILABILITY_FREE -> ShowAs.FREE
    AVAILABILITY_TENTATIVE -> ShowAs.TENTATIVE
    else -> ShowAs.BUSY
}

internal fun Int.toResponseStatus(): ResponseStatus = when (this) {
    ATTENDEE_ACCEPTED -> ResponseStatus.ACCEPTED
    ATTENDEE_DECLINED -> ResponseStatus.DECLINED
    ATTENDEE_TENTATIVE -> ResponseStatus.TENTATIVE
    ATTENDEE_INVITED -> ResponseStatus.NOT_RESPONDED
    else -> ResponseStatus.UNKNOWN
}

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val visible: Boolean,
)
