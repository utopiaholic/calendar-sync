package dev.albert.calmerge.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val type: AccountType,
    val displayName: String,
    val email: String?,
    /** ARGB color for UI badges (FR-19); user-pickable later. */
    val color: Int,
    /** The ICS feed URL — an unauthenticated capability URL, kept app-private. */
    val icsUrl: String? = null,
    val lastSyncUtc: Long? = null,
    val lastSyncError: String? = null,
    val status: AccountStatus = AccountStatus.ACTIVE,
)

@Entity(
    tableName = "calendar_sources",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("accountId"), Index(value = ["accountId", "providerCalendarId"], unique = true)],
)
data class CalendarSourceEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val providerCalendarId: String,
    val name: String,
    /** FR-5: user can exclude calendars (e.g. Birthdays). */
    val included: Boolean = true,
)

@Entity(
    tableName = "event_instances",
    foreignKeys = [ForeignKey(
        entity = CalendarSourceEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarSourceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["calendarSourceId", "providerEventId"], unique = true),
        Index("startUtc"),
        Index("iCalUId"),
    ],
)
data class EventInstanceEntity(
    @PrimaryKey val id: String,
    val calendarSourceId: String,
    /** Provider's instance id (expanded occurrence id for recurring events). */
    val providerEventId: String,
    /** Cross-account dedupe key when present (FR-13). */
    @ColumnInfo(name = "iCalUId") val iCalUId: String?,
    val title: String,
    /**
     * FR-11: timed events store UTC epoch millis; all-day events store date-only
     * ISO strings and leave the UTC fields null (never converted through UTC).
     */
    val startUtc: Long?,
    val endUtc: Long?,
    val isAllDay: Boolean,
    /** ISO-8601 local dates, end exclusive; only set when isAllDay. */
    val startDate: String?,
    val endDate: String?,
    val showAs: ShowAs,
    val responseStatus: ResponseStatus = ResponseStatus.UNKNOWN,
    val location: String?,
    val organizer: String?,
    val lastModifiedUtc: Long?,
    /** Events sharing a non-null group id are the same meeting seen from multiple accounts (FR-13). */
    val dedupeGroupId: String? = null,
)
