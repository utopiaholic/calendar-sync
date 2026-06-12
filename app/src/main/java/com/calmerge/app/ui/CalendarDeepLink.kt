package com.calmerge.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Returns a label for the deep-link button based on which calendar app is likely available. */
internal fun resolveDeepLinkLabel(accounts: List<EventDetailAccount>): String {
    val hosts = accounts.mapNotNull { it.icsHost }
    return when {
        CalendarProvider.MICROSOFT.matchesAny(hosts) -> "Open in Outlook"
        CalendarProvider.GOOGLE.matchesAny(hosts) -> "Open in Google Calendar"
        else -> "Open in calendar app"
    }
}

internal fun openInCalendarApp(
    context: Context,
    event: EventDetailModel,
    timeMs: Long,
) {
    val hosts = event.accounts.mapNotNull { it.icsHost }

    // Try Outlook if host suggests Microsoft.
    if (CalendarProvider.MICROSOFT.matchesAny(hosts)) {
        if (tryStart(context, outlookIntent(timeMs))) return
    }

    // Try Google Calendar if host suggests Google.
    if (CalendarProvider.GOOGLE.matchesAny(hosts)) {
        if (tryStart(context, googleCalendarIntent(timeMs))) return
    }

    // Generic AOSP calendar content URI.
    if (tryStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$timeMs")))) return

    // Nothing handled it: web fallback to Google Calendar day view for the event's date.
    val localDate = Instant.ofEpochMilli(timeMs)
        .atZone(ZoneId.systemDefault()).toLocalDate()
    val webUri = "https://calendar.google.com/calendar/r/day/${localDate.year}/${localDate.monthValue}/${localDate.dayOfMonth}"
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri))
    if (tryStart(context, webIntent)) return

    Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
}

private fun outlookIntent(timeMs: Long): Intent {
    // ms-outlook://events/view?start=<ISO-8601> is the documented Outlook mobile URI.
    val iso = Instant.ofEpochMilli(timeMs)
        .atZone(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    return Intent(Intent.ACTION_VIEW, Uri.parse("ms-outlook://events/view?start=${Uri.encode(iso)}"))
        .setPackage("com.microsoft.office.outlook")
}

private fun googleCalendarIntent(timeMs: Long): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/$timeMs"))
        .setPackage("com.google.android.calendar")

private fun tryStart(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
