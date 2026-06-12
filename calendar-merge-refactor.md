# CalMerge: Package Rename + Full Readability/Simplification Refactor

## Context

The app's package is `dev.albert.calmerge` — Albert's name is embedded in every package declaration, import, and source path. Goal: rename to **`com.calmerge.app`** and do a full readability/simplification pass so the code is professional and structured. Exploration confirmed the rename is mechanically safe (no reflection, no serialization by class name, manifest uses `${applicationId}` and relative class names, prefs/DB/WorkManager names don't embed the package).

Repo: `C:\code\Personal Calendar\calendar-sync` **is a git repo** (parent folder is not). Working tree is dirty: `AgendaScreen.kt`, `CalendarScreen.kt` modified, `ConflictBadge.kt` untracked — commit these first.

⚠️ The applicationId change creates a new app identity on the device: old install must be uninstalled (`adb uninstall dev.albert.calmerge`), feeds re-added after reinstall.

Per memory: **no Co-Authored-By trailers** in commits; coding/testing should run on **Sonnet** (flag /model switch after approval); after this milestone-style change, run the **fable reviewer → sonnet fixer → retest → commit** workflow if Albert asks.

## Commit plan (4 commits)

1. **Commit 0** — commit pending working-tree changes (conflict-badge work in flight). Never rename on a dirty tree. Optionally `git tag pre-rename`.
2. **Commit 1** — package rename only.
3. **Commit 2** — cleanup refactors (behavior-preserving except sanctioned normalizeUrl tightening + added error logging).
4. **Commit 3** — file splits (pure mechanical moves).

---

## Commit 1: Package rename `dev.albert.calmerge` → `com.calmerge.app`

1. `git mv` the three source trees (preserves history):
   - `app/src/main/java/dev/albert/calmerge` → `app/src/main/java/com/calmerge/app`
   - same for `app/src/test/java/...` and `app/src/androidTest/java/...`
   - remove leftover empty `dev/` dirs.
2. Scripted replace `dev.albert.calmerge` → `com.calmerge.app` in all `*.kt`, `*.kts`, `*.md` under `app/` and `docs/`. Use `[IO.File]::ReadAllText/WriteAllText` (avoids PowerShell 5.1 UTF-16 default, preserves LF). Covers 42 Kotlin files, `app/build.gradle.kts` (namespace line 9, applicationId line 13), `docs/preflight-results.md` line 72. Leave the gmail address on line 69 (not a package ref).
3. Verify: `Select-String 'dev\.albert\.calmerge'` over the repo (excluding `build/`) returns nothing; `.\gradlew.bat clean :app:assembleDebug :app:testDebugUnitTest`.

## Commit 2: Cleanup refactors

### B1. Provider detection dedup — new `ui/CalendarProvider.kt`
```kotlin
enum class CalendarProvider(private val hostMarkers: List<String>) {
    MICROSOFT(listOf("outlook", "office365", "microsoft")),
    GOOGLE(listOf("google", "googleapis"));
    fun matchesAny(hosts: List<String>): Boolean =
        hosts.any { host -> hostMarkers.any { host.contains(it, ignoreCase = true) } }
}
```
Boolean-per-provider (not a single `detect()`) because `openInCalendarApp` tries Outlook then Google when both match — preserves fall-through exactly. Replaces the 4 duplicated `hosts.any { ... }` blocks in `EventDetailSheet.kt` (~lines 100–127). Add `CalendarProviderTest.kt`.

### B2. Central parse helpers — new `ui/Parsing.kt`
```kotlin
fun parseIsoDateOrNull(value: String?): LocalDate?
fun isoDateToEpochMillisOrNull(value: String?, zone: ZoneId): Long?
fun urlHostOrNull(url: String?): String?
```
Migrate ~15 `runCatching { LocalDate.parse(...) }` call sites: `EventUi.kt` (20, 25, 46, 49), `ConflictClusterMapper.kt` (31–47 — extract shared `sortKeyMs`; the two blocks are identical), `TimeGridLayout.kt` (189, 191), `EventDetailSheet.kt` (57–59, 166), and the triplicated `icsHostById` map in CalendarScreen/AgendaScreen/ConflictsScreen.

Also dedupe the **triplicated `EventDetailModel` construction** (CalendarScreen 514–534, AgendaScreen 158–175, ConflictsScreen 108–125): new `ui/EventDetailModel.kt` holding `EventDetailModel` + `EventDetailAccount` (moved from EventDetailSheet.kt), `icsHostsByAccountId(...)`, and `toDetailModel(...)` overloads for `MergedEvent` and `ConflictMemberRow` delegating to one private builder. In `EventUi.kt`, add core overload `sortKeyMs(event: EventInstanceEntity, zone)` so ConflictClusterMapper shares it.

### B3. ConflictsScreen identical if/else (lines 171–185) — confirmed byte-identical branches
Collapse to a single expression using `parseIsoDateOrNull`; delete now-unused `hasAllDay` (line 140). Output unchanged.

### B4. Remove dead OAuth code
- `data/db/Enums.kt`: `enum class AccountType { ICS }` + KDoc noting ICS-only build.
- `sync/SyncCoordinator.kt` (~33–37): `when` collapses to direct `icsEngine.sync(account, window)`; drop the `error("OAuth account types are not supported...")` branch.
- **No Room migration needed**: `accounts.type` is TEXT, only ever written as `ICS` (app + tests), `exportSchema = false`, no schema change.
- **Keep** `AccountEntity.email` (dropping a column needs destructive migration) — KDoc as reserved/unused instead.

### B5. Consistent error handling
`MainViewModel.kt`: file-level `private const val TAG = "MainViewModel"` + one helper:
```kotlin
private suspend fun recomputeDerived(reason: String) {
    try { app.syncCoordinator.recomputeDerivedState() }
    catch (e: Exception) { Log.e(TAG, "recomputeDerivedState failed after $reason", e) }
}
```
Migrate all 6 call sites (lines ~131–206), replacing the silent `catch (_: Exception) {}` swallows. Import `Log` properly (kills fully-qualified `android.util.Log.e`). Remove unused imports (`SettingsStore`, `ZoneId`).
`work/SyncWorker.kt` line 27: log the swallowed exception before `Result.retry()`.

### B6. normalizeUrl tightening — new `ui/UrlNormalization.kt`
`normalizeUrlOrNull(raw: String): String?` — trims, lowercases scheme+authority only, strips one trailing slash, preserves query; returns **null** for blank/relative/host-less input (old code fell back to `lowercase()`, letting two garbage strings collide). Safe: `IcsDialog` already blocks submission without `https://` + valid host. Update `addIcsAccount` duplicate check accordingly.
**Update `NormalizeUrlTest.kt`**: rename calls; empty/whitespace now assert null; add cases for `"not a url"`, `"/relative/path.ics"`, valid https.

### B7. Misc
- KDoc the sentinels in `EventUi.kt` (`Long.MAX_VALUE` = unparseable sorts last; `LocalDate.MAX` = far-future header instead of crash).
- Delete dead `hourFmt` (CalendarScreen.kt line 57).
- Promote inline fully-qualified names in `EventDetailSheet.kt` (`java.time.Instant`, `ZoneOffset`, `DateTimeFormatter`, `Context`) to imports.
- Duplicated magic color `0xFF39D0C8.toInt()` (AgendaScreen 194, ConflictsScreen 257) → `internal val DefaultAccountColor` in `ui/theme/Color.kt`.

## Commit 3: File splits (mechanical; moved members go `private` → `internal`)

| Source (lines) | New files | Stays behind |
|---|---|---|
| `CalendarScreen.kt` (~535) | `CalendarGrid.kt` (HourGutter, DayColumn, grid constants HOUR_HEIGHT/GUTTER_WIDTH/TOTAL_HEIGHT), `AllDayStrip.kt` | CalendarScreen, CalendarMode, CalendarNavHeader, DayHeaderRow |
| `HomeScreen.kt` (~404) | `BentoCard.kt` (fix `androidx.compose.ui.unit.Dp` param → imported `Dp`) | HomeScreen, refactored in-file into private GreetingHeader/SummaryPill/FeedsCard/NextConflictCard/TodayCard sections |
| `ConflictsScreen.kt` (~353) | `ConflictClusterCard.kt` (card + BarData + OverlapBar + ConflictMemberItem + fmt helper) | ConflictsScreen + empty state |
| `FeedsScreen.kt` (~307) | `AccountRow.kt` (+ relativeMinutes), `IcsFeedDialog.kt` (rename IcsDialog → AddIcsFeedDialog), `ColorPickerDialog.kt` | FeedsScreen |
| `EventDetailSheet.kt` | `CalendarDeepLink.kt` (resolveDeepLinkLabel, openInCalendarApp, intents, tryStart) | EventDetailSheet composable + detailTimeText |

## Verification (after each commit)

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```
After Commit 1 only: `adb uninstall dev.albert.calmerge`, then install + relaunch as `com.calmerge.app/.ui.MainActivity`, re-add feeds via `scripts\serve-fixtures.ps1` + `scripts\add-feed.ps1`.
Device smoke per project convention: adb screenshots of Home/Agenda/Calendar/Conflicts tabs; `.\gradlew.bat :app:connectedDebugAndroidTest` with device attached.

Test coverage of refactored code: NormalizeUrlTest (B6, updated), EventUiWeekOverlapTest + ConflictClusterMapperTest + TimeGridLayoutTest (B2), sync tests (B4), ConflictsTabUiTest instrumented (B3 + ConflictsScreen split). New: CalendarProviderTest.

## Notes
- Commits: no Co-Authored-By trailers (Albert's repo convention).
- Suggest `/model sonnet` for the implementation phase per Albert's routing preference.
