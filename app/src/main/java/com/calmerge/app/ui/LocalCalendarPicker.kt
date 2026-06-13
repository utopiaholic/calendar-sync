package com.calmerge.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.calmerge.app.sync.DeviceCalendar
import com.calmerge.app.sync.LocalCalendarStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Full-screen dialog for selecting device calendars to import.
 * Permission is requested only on entry. Calendars already imported
 * (providerCalendarId in alreadyImportedSourceIds) are shown checked + disabled.
 */
@Composable
fun LocalCalendarPicker(
    viewModel: MainViewModel,
    alreadyImportedSourceIds: Set<String>,
    onNeedHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalCalendarStore(context) }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var calendars by remember { mutableStateOf<List<DeviceCalendar>>(emptyList()) }
    var quality by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val outlookInstalled = remember {
        context.packageManager.getLaunchIntentForPackage(OUTLOOK_PACKAGE) != null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionGranted = true
        } else {
            permissionDenied = true
        }
    }

    suspend fun loadCalendars() {
        loading = true
        val loaded = withContext(Dispatchers.IO) { store.listCalendars() }
        val previousCalendarIds = calendars.map { it.id }.toSet()
        val importableIds = loaded
            .filter { it.id.toString() !in alreadyImportedSourceIds }
            .map { it.id }
            .toSet()
        val newlyDiscoveredIds = importableIds - previousCalendarIds
        calendars = loaded
        selected = (selected intersect importableIds) + newlyDiscoveredIds
        loading = false

        scope.launch(Dispatchers.IO) {
            val now = Instant.now()
            val window = com.calmerge.app.sync.SyncWindow.around(now, daysPast = 0, daysFuture = 30)
            val q = loaded.associate { cal ->
                val events = store.queryEvents(cal.id, cal.id.toString(), window)
                val titles = events.map { it.title.takeIf { t -> !t.isNullOrBlank() } }
                cal.id to classifyCalendarQuality(titles, events.size)
            }
            withContext(Dispatchers.Main) { quality = q }
        }
    }

    LaunchedEffect(permissionGranted) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            return@LaunchedEffect
        }
        loadCalendars()
    }

    // Refresh when the user returns from another app (e.g. after enabling Outlook sync)
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeCount by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumeCount) {
        if (resumeCount <= 1 || !permissionGranted) return@LaunchedEffect
        loadCalendars()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Select calendars", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                }
                HorizontalDivider()

                when {
                    permissionDenied -> PermissionDeniedMessage(context.packageName)
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    calendars.isEmpty() -> EmptyCalendarsMessage(onNeedHelp)
                    else -> {
                        val grouped = calendars.groupBy { it.accountName }
                        val showOutlookPrompt = outlookInstalled && calendars.none { isOutlookCalendar(it) }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        ) {
                            grouped.forEach { (accountName, cals) ->
                                item {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        accountName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                items(cals, key = { it.id }) { cal ->
                                    val alreadyImported = cal.id.toString() in alreadyImportedSourceIds
                                    val checked = alreadyImported || cal.id in selected
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { on ->
                                                if (!alreadyImported) {
                                                    selected = if (on) selected + cal.id else selected - cal.id
                                                }
                                            },
                                            enabled = !alreadyImported,
                                        )
                                        CalendarProviderIcon(cal)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                cal.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            val badge = when {
                                                alreadyImported -> "Imported"
                                                else -> quality[cal.id]
                                            }
                                            badge?.let {
                                                Text(
                                                    it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }
                            }
                            item {
                                MissingCalendarHelp(
                                    showOutlookPrompt = showOutlookPrompt,
                                    onNeedHelp = onNeedHelp,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
                                )
                            }
                        }

                        HorizontalDivider()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 56.dp),
                        ) {
                            Button(
                                onClick = {
                                    val toImport = calendars.filter { it.id in selected }
                                    viewModel.addLocalCalendars(toImport)
                                },
                                enabled = selected.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                val count = selected.size.takeIf { it > 0 }?.let { "$it " }.orEmpty()
                                Text("Import ${count}calendar${if (selected.size != 1) "s" else ""}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calendarIconPackage(cal: DeviceCalendar): String? {
    val marker = "${cal.accountType} ${cal.accountName}".lowercase()
    return when {
        marker.contains("exchange") ||
            marker.contains("outlook") ||
            marker.contains("microsoft") -> OUTLOOK_PACKAGE
        marker.contains("google") ||
            marker.contains("gmail") -> GOOGLE_CALENDAR_PACKAGE
        else -> null
    }
}

@Composable
private fun CalendarProviderIcon(cal: DeviceCalendar) {
    AppIcon(
        packageName = calendarIconPackage(cal),
        contentDescription = null,
        size = 28.dp,
    )
}

@Composable
private fun EmptyCalendarsMessage(onNeedHelp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No calendars found",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "No calendar apps have shared their calendars with Android yet. If you expected Outlook, it may need Sync Calendars turned on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onNeedHelp) {
            Text("Don't see your calendar?")
        }
    }
}

@Composable
private fun MissingCalendarHelp(
    showOutlookPrompt: Boolean,
    onNeedHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showOutlookPrompt) {
            Text(
                "Have Outlook? Its calendars may not be shared with Android yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
        }
        TextButton(
            onClick = onNeedHelp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (showOutlookPrompt) "Show Outlook setup steps" else "Don't see your calendar?",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun InstructionSteps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedMessage(packageName: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Calendar access was denied.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "To import calendars from this phone, grant the Calendar permission in app settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                },
            )
        }) {
            Text("Open app settings")
        }
    }
}

/**
 * Pure classifier — takes only the list of event titles and count so it can be
 * unit-tested without any Android dependencies.
 */
internal fun classifyCalendarQuality(titles: List<String?>, count: Int): String {
    if (count == 0) return "No upcoming events found"
    val genericTitles = setOf("busy", "tentative", "free", "out of office", "")
    val hasDetails = titles.any { t ->
        t != null && t.trim().lowercase() !in genericTitles
    }
    return if (hasDetails) {
        "$count upcoming · Details available"
    } else {
        "$count upcoming · Busy only"
    }
}
