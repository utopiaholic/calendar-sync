package com.calmerge.app.ui

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.calmerge.app.data.db.ShowAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun LocalCalendarProbe() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<LocalCalendarProbeResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun runProbe() {
        loading = true
        error = null
        scope.launch {
            try {
                result = queryLocalCalendars(context)
            } catch (t: Throwable) {
                error = t.message ?: t::class.java.simpleName
            } finally {
                loading = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            runProbe()
        } else {
            error = "Calendar permission denied."
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Local calendar probe", style = MaterialTheme.typography.titleSmall)
            Text(
                "Checks what Android exposes from calendars already synced on this phone. This does not import or save events.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        runProbe()
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Checking..." else "Check local calendars")
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            result?.let { LocalCalendarProbeResultView(it) }
        }
    }
}

@Composable
private fun LocalCalendarProbeResultView(result: LocalCalendarProbeResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${result.calendars.size} calendars visible, ${result.events.size} sample events found",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        result.calendars.take(12).forEach { calendar ->
            Text(
                "${calendar.name} | ${calendar.accountName} | ${calendar.accountType}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (result.events.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Sample event details", style = MaterialTheme.typography.titleSmall)
            result.events.take(10).forEach { event ->
                Column(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(event.title ?: "(no title)", style = MaterialTheme.typography.bodyMedium)
                        Text(event.showAs.name, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(event.whenText, style = MaterialTheme.typography.bodySmall)
                    event.calendarName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!event.description.isNullOrBlank()) {
                        Text("Description: ${event.description.take(120)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!event.location.isNullOrBlank()) {
                        Text("Location: ${event.location}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private suspend fun queryLocalCalendars(context: Context): LocalCalendarProbeResult =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val calendars = resolver.queryCalendars()
        val events = resolver.queryInstances()
        LocalCalendarProbeResult(calendars = calendars, events = events)
    }

private fun ContentResolver.queryCalendars(): List<LocalCalendarInfo> {
    val projection = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.ACCOUNT_NAME,
        CalendarContract.Calendars.ACCOUNT_TYPE,
        CalendarContract.Calendars.VISIBLE,
    )
    return query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        null,
        null,
        "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    LocalCalendarInfo(
                        id = cursor.getLong(0),
                        name = cursor.getString(1).orEmpty(),
                        accountName = cursor.getString(2).orEmpty(),
                        accountType = cursor.getString(3).orEmpty(),
                        visible = cursor.getInt(4) == 1,
                    ),
                )
            }
        }
    }.orEmpty()
}

private fun ContentResolver.queryInstances(): List<LocalCalendarEventSample> {
    val now = System.currentTimeMillis()
    val start = now - 7L * 24 * 60 * 60 * 1000
    val end = now + 30L * 24 * 60 * 60 * 1000
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, start)
        ContentUris.appendId(it, end)
    }.build()
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.CALENDAR_ID,
        CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.DESCRIPTION,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.AVAILABILITY,
    )
    return query(
        uri,
        projection,
        null,
        null,
        "${CalendarContract.Instances.BEGIN} ASC",
    )?.use { cursor ->
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        buildList {
            while (cursor.moveToNext() && size < 25) {
                val begin = cursor.getLong(6)
                val endMs = cursor.getLong(7)
                add(
                    LocalCalendarEventSample(
                        eventId = cursor.getLong(0),
                        calendarId = cursor.getLong(1),
                        calendarName = cursor.getString(2),
                        title = cursor.getString(3),
                        description = cursor.getString(4),
                        location = cursor.getString(5),
                        whenText = "${dateFormat.format(Date(begin))} - ${dateFormat.format(Date(endMs))}",
                        isAllDay = cursor.getInt(8) == 1,
                        showAs = cursor.getInt(9).toShowAs(),
                    ),
                )
            }
        }
    }.orEmpty()
}

private fun Int.toShowAs(): ShowAs =
    when (this) {
        CalendarContract.Events.AVAILABILITY_FREE -> ShowAs.FREE
        CalendarContract.Events.AVAILABILITY_TENTATIVE -> ShowAs.TENTATIVE
        else -> ShowAs.BUSY
    }

private data class LocalCalendarProbeResult(
    val calendars: List<LocalCalendarInfo>,
    val events: List<LocalCalendarEventSample>,
)

private data class LocalCalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val visible: Boolean,
)

private data class LocalCalendarEventSample(
    val eventId: Long,
    val calendarId: Long,
    val calendarName: String?,
    val title: String?,
    val description: String?,
    val location: String?,
    val whenText: String,
    val isAllDay: Boolean,
    val showAs: ShowAs,
)
