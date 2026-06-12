package dev.albert.calmerge.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists user-adjustable settings via SharedPreferences.
 * Exposed as StateFlow so Compose screens react to changes without restarting.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("calmerge_settings", Context.MODE_PRIVATE)

    // ---- Sync interval (FR-9) ----
    /** 15, 30, 60 minutes or MANUAL (0 = no periodic work). */
    private val _syncIntervalMinutes = MutableStateFlow(
        prefs.getLong(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL)
    )
    val syncIntervalMinutes: StateFlow<Long> = _syncIntervalMinutes.asStateFlow()

    fun setSyncInterval(minutes: Long) {
        prefs.edit().putLong(KEY_SYNC_INTERVAL, minutes).apply()
        _syncIntervalMinutes.value = minutes
    }

    // ---- Conflict config (FR-14, FR-15) ----
    private val _includeTentative = MutableStateFlow(prefs.getBoolean(KEY_INCLUDE_TENTATIVE, true))
    val includeTentative: StateFlow<Boolean> = _includeTentative.asStateFlow()

    fun setIncludeTentative(v: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_TENTATIVE, v).apply()
        _includeTentative.value = v
    }

    private val _includeOof = MutableStateFlow(prefs.getBoolean(KEY_INCLUDE_OOF, true))
    val includeOof: StateFlow<Boolean> = _includeOof.asStateFlow()

    fun setIncludeOof(v: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_OOF, v).apply()
        _includeOof.value = v
    }

    /** FR-15 default: all-day events do NOT conflict with timed events. */
    private val _allDayConflictsWithTimed = MutableStateFlow(
        prefs.getBoolean(KEY_ALLDAY_CONFLICTS_TIMED, false)
    )
    val allDayConflictsWithTimed: StateFlow<Boolean> = _allDayConflictsWithTimed.asStateFlow()

    fun setAllDayConflictsWithTimed(v: Boolean) {
        prefs.edit().putBoolean(KEY_ALLDAY_CONFLICTS_TIMED, v).apply()
        _allDayConflictsWithTimed.value = v
    }

    companion object {
        const val DEFAULT_SYNC_INTERVAL = 30L
        const val MANUAL_SYNC = 0L

        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private const val KEY_INCLUDE_TENTATIVE = "conflict_include_tentative"
        private const val KEY_INCLUDE_OOF = "conflict_include_oof"
        private const val KEY_ALLDAY_CONFLICTS_TIMED = "conflict_allday_vs_timed"
    }
}
