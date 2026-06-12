package com.calmerge.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.calmerge.app.CalMergeApp
import com.calmerge.app.data.db.AccountEntity
import com.calmerge.app.data.db.AccountType
import com.calmerge.app.data.db.CalendarSourceEntity
import com.calmerge.app.data.db.ConflictClusterEntity
import com.calmerge.app.data.db.ConflictMemberEntity
import com.calmerge.app.data.db.EventInstanceEntity
import com.calmerge.app.data.db.ResponseStatus
import com.calmerge.app.data.db.ShowAs
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Instrumented Compose UI tests for the Conflicts tab (FR-21, FR-22).
 *
 * Data is seeded directly into Room before each test — no network calls needed.
 * The test verifies what a user actually sees when the app has a real conflict
 * in the database.
 */
@RunWith(AndroidJUnit4::class)
class ConflictsTabUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app get() = composeRule.activity.application as CalMergeApp

    // Stable IDs for assertions
    private val workAAccountId = "acc-workA"
    private val workBAccountId = "acc-workB"
    private val personalAccountId = "acc-personal"
    private val workSourceId = "src-workA"
    private val workBSourceId = "src-workB"
    private val personalSourceId = "src-personal"
    private val sharedMeetingIdA = "evt-shared-A"
    private val sharedMeetingIdB = "evt-shared-B"
    private val dentistId = "evt-dentist"
    private val gymId = "evt-gym"
    private val clusterId = "cluster-1"

    @Before
    fun seedDatabase() = runBlocking {
        val db = app.db

        // Clear any pre-existing data from previous test runs or from actual feeds.
        db.conflictDao().deleteAll()
        db.accountDao().delete(workAAccountId)
        db.accountDao().delete(workBAccountId)
        db.accountDao().delete(personalAccountId)

        // Accounts
        db.accountDao().upsert(
            AccountEntity(
                id = workAAccountId, type = AccountType.ICS,
                displayName = "Work A", email = null, color = 0xFF1A73E8.toInt(),
            ),
        )
        db.accountDao().upsert(
            AccountEntity(
                id = workBAccountId, type = AccountType.ICS,
                displayName = "Work B", email = null, color = 0xFFD93025.toInt(),
            ),
        )
        db.accountDao().upsert(
            AccountEntity(
                id = personalAccountId, type = AccountType.ICS,
                displayName = "Personal", email = null, color = 0xFF188038.toInt(),
            ),
        )

        // Calendar sources
        db.calendarSourceDao().insertIgnore(
            listOf(
                CalendarSourceEntity(id = workSourceId, accountId = workAAccountId,
                    providerCalendarId = "ics", name = "Work A"),
                CalendarSourceEntity(id = workBSourceId, accountId = workBAccountId,
                    providerCalendarId = "ics", name = "Work B"),
                CalendarSourceEntity(id = personalSourceId, accountId = personalAccountId,
                    providerCalendarId = "ics", name = "Personal"),
            ),
        )

        // Events from testdata/*.ics:
        // Cross-team planning: Work A + Work B, 14:00–15:00 Jun 12, same UID → deduped
        val planningStart = Instant.parse("2026-06-12T14:00:00Z").toEpochMilli()
        val planningEnd = Instant.parse("2026-06-12T15:00:00Z").toEpochMilli()
        db.eventInstanceDao().upsertAll(
            listOf(
                EventInstanceEntity(
                    id = sharedMeetingIdA, calendarSourceId = workSourceId,
                    providerEventId = "shared-1#$planningStart",
                    iCalUId = "shared-meeting-001@example.com",
                    title = "Cross-team planning",
                    startUtc = planningStart, endUtc = planningEnd,
                    isAllDay = false, startDate = null, endDate = null,
                    showAs = ShowAs.BUSY, responseStatus = ResponseStatus.ACCEPTED,
                    location = "Teams", organizer = null, lastModifiedUtc = null,
                    dedupeGroupId = "grp-shared",
                ),
                EventInstanceEntity(
                    id = sharedMeetingIdB, calendarSourceId = workBSourceId,
                    providerEventId = "shared-1#$planningStart",
                    iCalUId = "shared-meeting-001@example.com",
                    title = "Cross-team planning",
                    startUtc = planningStart, endUtc = planningEnd,
                    isAllDay = false, startDate = null, endDate = null,
                    showAs = ShowAs.BUSY, responseStatus = ResponseStatus.ACCEPTED,
                    location = "Teams", organizer = null, lastModifiedUtc = null,
                    dedupeGroupId = "grp-shared",
                ),
                // Dentist: Personal, 14:30–15:30 Jun 12 — overlaps with planning
                EventInstanceEntity(
                    id = dentistId, calendarSourceId = personalSourceId,
                    providerEventId = "dentist-1#${Instant.parse("2026-06-12T14:30:00Z").toEpochMilli()}",
                    iCalUId = "personal-dentist-001@example.com",
                    title = "Dentist appointment",
                    startUtc = Instant.parse("2026-06-12T14:30:00Z").toEpochMilli(),
                    endUtc = Instant.parse("2026-06-12T15:30:00Z").toEpochMilli(),
                    isAllDay = false, startDate = null, endDate = null,
                    showAs = ShowAs.BUSY, responseStatus = ResponseStatus.ACCEPTED,
                    location = null, organizer = null, lastModifiedUtc = null,
                ),
                // Gym: Personal, FREE — must NOT appear in conflicts
                EventInstanceEntity(
                    id = gymId, calendarSourceId = personalSourceId,
                    providerEventId = "gym-1",
                    iCalUId = "personal-gym-001@example.com",
                    title = "Gym",
                    startUtc = Instant.parse("2026-06-13T10:00:00Z").toEpochMilli(),
                    endUtc = Instant.parse("2026-06-13T11:00:00Z").toEpochMilli(),
                    isAllDay = false, startDate = null, endDate = null,
                    showAs = ShowAs.FREE, responseStatus = ResponseStatus.ACCEPTED,
                    location = null, organizer = null, lastModifiedUtc = null,
                ),
            ),
        )

        // Seed conflict cluster: Cross-team planning (both copies) + Dentist
        db.conflictDao().replaceAll(
            clusters = listOf(
                ConflictClusterEntity(
                    id = clusterId,
                    computedAtUtc = Instant.now().toEpochMilli(),
                ),
            ),
            members = listOf(
                ConflictMemberEntity(clusterId, sharedMeetingIdA),
                ConflictMemberEntity(clusterId, sharedMeetingIdB),
                ConflictMemberEntity(clusterId, dentistId),
            ),
        )
    }

    @Test
    fun conflictsTab_showsClusterWithBothEventTitles() {
        // Navigate to Conflicts tab
        composeRule.onNodeWithText("Conflicts").performClick()
        composeRule.waitForIdle()

        // The conflict card header is shown (red date + overlap text)
        composeRule.onNodeWithText("Fri, Jun 12", substring = true).assertIsDisplayed()

        // Both event titles appear in the cluster card
        composeRule.onNodeWithText("Cross-team planning").assertIsDisplayed()
        composeRule.onNodeWithText("Dentist appointment").assertIsDisplayed()
    }

    @Test
    fun conflictsTab_gymNotShown() {
        composeRule.onNodeWithText("Conflicts").performClick()
        composeRule.waitForIdle()

        // Gym is FREE so it must not appear anywhere in the Conflicts tab
        val gymNodes = composeRule.onAllNodesWithText("Gym").fetchSemanticsNodes()
        assert(gymNodes.isEmpty()) { "Gym (FREE) must not appear in Conflicts but found ${gymNodes.size} node(s)" }
    }

    @Test
    fun conflictsTab_badge_showsCountOnTabRow() {
        // The Conflicts tab badge should show "1" (one cluster)
        composeRule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun conflictsTab_tapEvent_opensDetailSheet() {
        composeRule.onNodeWithText("Conflicts").performClick()
        composeRule.waitForIdle()

        // Tap the Dentist row — there is exactly one in the Conflicts list
        composeRule.onAllNodesWithText("Dentist appointment")[0].performClick()
        composeRule.waitForIdle()

        // Detail sheet is open: assert sheet-only content (Show-as only appears in the sheet)
        composeRule.onNodeWithText("Show-as: BUSY", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Account:", substring = true).assertIsDisplayed()
    }

    @Test
    fun agendaTab_conflictingEventsHaveBadge() {
        // Agenda tab is default — at least one cross-team planning row should be visible.
        // Use onAllNodesWithText because deduped copies may both appear in the flat DB.
        composeRule.onAllNodesWithText("Cross-team planning")[0].assertIsDisplayed()
        // At least one CONFLICT badge must be visible (both conflicting events get one)
        composeRule.onAllNodesWithText("CONFLICT")[0].assertIsDisplayed()
    }
}
