package com.calmerge.app.ui

import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.ConflictMemberRow
import com.calmerge.app.data.db.MergedEvent

data class EventDetailModel(
    val title: String,
    val startUtc: Long?,
    val endUtc: Long?,
    val isAllDay: Boolean,
    val startDate: String?,
    val location: String?,
    val organizer: String?,
    val showAs: String,
    val accounts: List<EventDetailAccount>,
)

data class EventDetailAccount(
    val id: String,
    val name: String,
    val type: AccountType,
    /** Host of the ICS URL, used for deep-link heuristics (FR-22). Null for non-ICS accounts. */
    val icsHost: String? = null,
)

fun icsHostsByAccountId(accounts: List<AccountEntity>): Map<String, String?> =
    accounts.associate { account -> account.id to urlHostOrNull(account.icsUrl) }

fun MergedEvent.toDetailModel(
    copies: List<MergedEvent>,
    icsHostsByAccountId: Map<String, String?> = emptyMap(),
): EventDetailModel = buildDetailModel(
    title = event.title,
    startUtc = event.startUtc,
    endUtc = event.endUtc,
    isAllDay = event.isAllDay,
    startDate = event.startDate,
    location = event.location,
    organizer = event.organizer,
    showAs = event.showAs.name,
    accounts = copies.distinctBy { it.accountId }.map {
        EventDetailAccount(
            id = it.accountId,
            name = it.accountName,
            type = it.accountType,
            icsHost = icsHostsByAccountId[it.accountId],
        )
    },
)

fun ConflictMemberRow.toDetailModel(
    copies: List<ConflictMemberRow>,
    icsHostsByAccountId: Map<String, String?> = emptyMap(),
): EventDetailModel = buildDetailModel(
    title = event.title,
    startUtc = event.startUtc,
    endUtc = event.endUtc,
    isAllDay = event.isAllDay,
    startDate = event.startDate,
    location = event.location,
    organizer = event.organizer,
    showAs = event.showAs.name,
    accounts = copies.distinctBy { it.accountId }.map {
        EventDetailAccount(
            id = it.accountId,
            name = it.accountName,
            type = it.accountType,
            icsHost = icsHostsByAccountId[it.accountId],
        )
    },
)

private fun buildDetailModel(
    title: String,
    startUtc: Long?,
    endUtc: Long?,
    isAllDay: Boolean,
    startDate: String?,
    location: String?,
    organizer: String?,
    showAs: String,
    accounts: List<EventDetailAccount>,
) = EventDetailModel(
    title = title,
    startUtc = startUtc,
    endUtc = endUtc,
    isAllDay = isAllDay,
    startDate = startDate,
    location = location,
    organizer = organizer,
    showAs = showAs,
    accounts = accounts,
)
