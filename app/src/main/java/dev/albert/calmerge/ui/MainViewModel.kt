package dev.albert.calmerge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.albert.calmerge.CalMergeApp
import dev.albert.calmerge.data.db.AccountEntity
import dev.albert.calmerge.data.db.AccountType
import dev.albert.calmerge.data.db.CalendarSourceEntity
import dev.albert.calmerge.data.db.MergedEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(private val app: CalMergeApp) : ViewModel() {

    /** Distinct badge colors assigned to feeds in connect order; cycles if exhausted. */
    private val palette = listOf(
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

    val syncing = MutableStateFlow(false)

    fun addIcsAccount(name: String, url: String) {
        viewModelScope.launch {
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
            syncNow()
        }
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
        viewModelScope.launch { app.db.calendarSourceDao().setIncluded(sourceId, included) }
    }

    /** NFR-6: disconnecting removes the feed URL and (via FK cascade) all cached events. */
    fun removeAccount(account: AccountEntity) {
        viewModelScope.launch { app.db.accountDao().delete(account.id) }
    }

    private fun nextColor(): Int = palette[accounts.value.size % palette.size]

    companion object {
        fun factory(app: CalMergeApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(app) as T
        }
    }
}
