package dev.albert.calmerge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.albert.calmerge.CalMergeApp
import dev.albert.calmerge.data.db.AccountEntity
import dev.albert.calmerge.data.db.AccountType
import dev.albert.calmerge.data.db.CalendarSourceEntity
import dev.albert.calmerge.data.db.ConflictMemberRow
import dev.albert.calmerge.data.db.MergedEvent
import dev.albert.calmerge.settings.SettingsStore
import dev.albert.calmerge.work.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

enum class AgendaFilter { WEEK, ALL }

/** One conflict cluster prepared for display, soonest first (FR-21). */
data class ConflictClusterUi(
    val clusterId: String,
    /** Members collapsed by dedupe group: representative + all copies. */
    val members: List<Pair<ConflictMemberRow, List<ConflictMemberRow>>>,
    val sortKeyMs: Long,
)

class MainViewModel(private val app: CalMergeApp) : ViewModel() {

    /** Distinct badge colors assigned to feeds in connect order; cycles by usage count only when palette exhausted. */
    val palette = listOf(
        0xFF1A73E8.toInt(), // blue
        0xFFD93025.toInt(), // red
        0xFF188038.toInt(), // green
        0xFFF9AB00.toInt(), // amber
        0xFF9334E6.toInt(), // purple
        0xFF12A4AF.toInt(), // teal
        0xFFE8710A.toInt(), // orange
        0xFFB80672.toInt(), // magenta
        0xFF5F6368.toInt(), // grey
        0xFF7CB342.toInt(), // light green
    )

    val accounts: StateFlow<List<AccountEntity>> = app.db.accountDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sources: StateFlow<List<CalendarSourceEntity>> = app.db.calendarSourceDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mergedEvents: StateFlow<List<MergedEvent>> = app.db.eventInstanceDao().observeMerged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Event ids carrying a red conflict badge in the agenda (FR-19). */
    val conflictedEventIds: StateFlow<Set<String>> = app.db.conflictDao().observeConflictedEventIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val conflictClusters: StateFlow<List<ConflictClusterUi>> = app.db.conflictDao().observeConflictMembers()
        .map { rows -> ConflictClusterMapper.toClusterUi(rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Default WEEK: limits the agenda to the current week for easier scanning/testing. */
    val agendaFilter = MutableStateFlow(AgendaFilter.WEEK)

    val syncing = MutableStateFlow(false)

    /**
     * Non-null when addIcsAccount rejects a duplicate URL (FR-3).
     * Cleared by the dialog on dismiss or when url input changes.
     */
    val addFeedError = MutableStateFlow<String?>(null)

    /**
     * Flips to true momentarily when an account is successfully inserted so the
     * dialog can close itself. Reset to false after consumption.
     */
    val addFeedSuccess = MutableStateFlow(false)

    fun addIcsAccount(name: String, url: String) {
        viewModelScope.launch {
            val normalized = normalizeUrl(url)
            val duplicate = accounts.value.any { normalizeUrl(it.icsUrl ?: "") == normalized }
            if (duplicate) {
                addFeedError.value = "This feed is already added"
                return@launch
            }
            addFeedError.value = null
            app.db.accountDao().upsert(
                AccountEntity(
                    id = UUID.randomUUID().toString(),
                    type = AccountType.ICS,
                    displayName = name.ifBlank { "ICS feed" },
                    email = null,
                    color = nextColor(),
                    icsUrl = url.trim(),
                ),
            )
            addFeedSuccess.value = true
            syncNow()
        }
    }

    fun clearAddFeedError() {
        addFeedError.value = null
    }

    fun consumeAddFeedSuccess() {
        addFeedSuccess.value = false
    }

    fun syncNow() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            try {
                app.syncCoordinator.syncAll()
            } finally {
                syncing.value = false
            }
        }
    }

    fun setSourceIncluded(sourceId: String, included: Boolean) {
        viewModelScope.launch {
            app.db.calendarSourceDao().setIncluded(sourceId, included)
            try {
                app.syncCoordinator.recomputeDerivedState()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "recomputeDerivedState failed after setSourceIncluded", e)
            }
        }
    }

    // ---- Settings delegation ----

    val syncIntervalMinutes: StateFlow<Long> = app.settings.syncIntervalMinutes
    val includeTentative: StateFlow<Boolean> = app.settings.includeTentative
    val includeOof: StateFlow<Boolean> = app.settings.includeOof
    val allDayConflictsWithTimed: StateFlow<Boolean> = app.settings.allDayConflictsWithTimed

    fun setSyncInterval(minutes: Long) {
        app.settings.setSyncInterval(minutes)
        if (minutes > 0) {
            SyncScheduler.ensurePeriodicSync(app, minutes)
        } else {
            // Cancel periodic work when user selects "Manual only".
            SyncScheduler.cancelPeriodicSync(app)
        }
    }

    fun setIncludeTentative(v: Boolean) {
        app.settings.setIncludeTentative(v)
        viewModelScope.launch {
            try { app.syncCoordinator.recomputeDerivedState() } catch (_: Exception) {}
        }
    }

    fun setIncludeOof(v: Boolean) {
        app.settings.setIncludeOof(v)
        viewModelScope.launch {
            try { app.syncCoordinator.recomputeDerivedState() } catch (_: Exception) {}
        }
    }

    fun setAllDayConflictsWithTimed(v: Boolean) {
        app.settings.setAllDayConflictsWithTimed(v)
        viewModelScope.launch {
            try { app.syncCoordinator.recomputeDerivedState() } catch (_: Exception) {}
        }
    }

    /** NFR-6: wipe all accounts and events (cascade). */
    fun wipeAllData() {
        viewModelScope.launch {
            // Query inside the coroutine — accounts.value can be empty if no collector
            // has subscribed (e.g. after process-death restore to Settings tab).
            app.db.accountDao().getAll().forEach { account ->
                app.db.accountDao().delete(account.id)
            }
            try { app.syncCoordinator.recomputeDerivedState() } catch (_: Exception) {}
        }
    }

    /** FR-19: user-pickable account color. Targeted update avoids clobbering a
     *  concurrently-written sync status (lastSyncUtc / lastSyncError). */
    fun updateAccountColor(accountId: String, color: Int) {
        viewModelScope.launch {
            app.db.accountDao().updateColor(accountId, color)
        }
    }

    /** NFR-6: disconnecting removes the feed URL and (via FK cascade) all cached events. */
    fun removeAccount(account: AccountEntity) {
        viewModelScope.launch {
            app.db.accountDao().delete(account.id)
            try {
                app.syncCoordinator.recomputeDerivedState()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "recomputeDerivedState failed after removeAccount", e)
            }
        }
    }

    /** Pick the first palette color not currently used; cycle by usage count only when all are taken. */
    private fun nextColor(): Int {
        val usedColors = accounts.value.map { it.color }.toSet()
        val unused = palette.firstOrNull { it !in usedColors }
        if (unused != null) return unused
        // All palette colors are in use — cycle by least-used.
        val usageCounts = palette.associateWith { color -> accounts.value.count { it.color == color } }
        return palette.minByOrNull { usageCounts[it] ?: 0 } ?: palette[0]
    }

    companion object {
        fun factory(app: CalMergeApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
        }
    }
}

/**
 * Normalizes a URL for duplicate detection: trims whitespace, lowercases
 * scheme+host, strips a single trailing slash. Pure function — no Android
 * framework classes — so it is unit-testable on the JVM.
 */
fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return try {
        val uri = java.net.URI(trimmed)
        val scheme = (uri.scheme ?: "").lowercase()
        val authority = (uri.authority ?: "").lowercase()
        val path = uri.path ?: ""
        val normalizedPath = if (path.endsWith("/") && path.length > 1) path.dropLast(1) else path
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        "$scheme://$authority$normalizedPath$query"
    } catch (_: Exception) {
        trimmed.lowercase()
    }
}
