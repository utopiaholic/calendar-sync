# CalMerge — Read-Only Calendar Aggregator for Android

## Requirements Specification v1.0

---

## 1. Overview

A privacy-first Android app that aggregates calendars from **two Microsoft 365 (work) Outlook accounts** and **one personal Google account** into a single unified, **read-only** view. The app's primary job is to surface **scheduling conflicts** across the three calendars so the user can resolve them manually in the native apps.

**Explicit non-goals:** No event creation, editing, deletion, or two-way sync. No busy-blocking. No third-party backend — the app talks only to Microsoft Graph and Google Calendar APIs directly from the device.

## 2. User Profile & Context

- Single user (personal tool), senior developer, comfortable self-managing OAuth app registrations.
- Device: Android (assume Android 12+ / API 31+ minimum).
- Accounts: 2× Microsoft 365 work accounts (different tenants possible), 1× personal Google account.

## 3. Critical Pre-Flight Check (do this before writing code)

The two Outlook accounts are **corporate M365 tenants**. Tenant admins may block user consent for third-party app registrations. Before building anything:

1. Register a **multi-tenant app** in a personal Azure account (Entra ID, free tier) requesting only `Calendars.Read` (delegated) + `offline_access` + `User.Read`.
2. Attempt sign-in with each work account using a minimal MSAL test (or Graph Explorer) to verify consent is granted or admin approval is obtainable.
3. **Fallback if Graph is blocked:** subscribe to the published ICS feed for each work calendar (Outlook Web → Settings → Shared calendars → Publish a calendar). ICS publishing is sometimes allowed even when app consent is not. The architecture below MUST support ICS-feed accounts as a degraded source type (read-only, no delta sync, coarser refresh).

If neither Graph nor ICS is available for a tenant, that account cannot be supported — surface this clearly in onboarding.

## 4. Tech Stack

- **Primary:** .NET MAUI (Android target only) with C# — leverages first-class MSAL.NET and Microsoft Graph SDK support.
  - Acceptable alternative if MAUI friction is high: native Kotlin + MSAL Android + Google AppAuth.
- **Local storage:** SQLite (`sqlite-net-pcl` or EF Core), database file in app-private storage.
- **Auth token storage:** MSAL's default encrypted cache + Android Keystore; Google refresh token in Android Keystore-backed encrypted prefs.
- **Background refresh:** WorkManager (via MAUI/native interop) with a periodic sync job.

## 5. Functional Requirements

### 5.1 Account Connection (Onboarding)

- FR-1: Connect up to 2 Microsoft accounts via MSAL interactive flow (multi-account public client). Scope: `Calendars.Read`, `offline_access`, `User.Read`.
- FR-2: Connect 1 Google account via AppAuth/Google Sign-In. Scope: `https://www.googleapis.com/auth/calendar.readonly`. Google Cloud project stays in "Testing" mode (personal use; no verification needed; refresh tokens for test users expire after ~7 days in testing mode — see FR-4).
- FR-3: Support adding an **ICS feed URL** as an account type (fallback per §3).
- FR-4: Detect expired/revoked tokens and prompt re-authentication with a non-intrusive banner, not a crash. Note: Google testing-mode token expiry makes graceful re-auth a first-class requirement, not an edge case.
- FR-5: Per account, let the user choose which calendars within the account to include (e.g., skip "Birthdays", shared team calendars).

### 5.2 Sync (One-Way, Pull Only)

- FR-6: Microsoft accounts: use Graph **delta queries** (`/me/calendarView/delta`) over a rolling window (default: 7 days past → 60 days future, configurable).
- FR-7: Google account: use `events.list` with `syncToken` incremental sync over the same window.
- FR-8: ICS accounts: full re-download and re-parse each refresh (no incremental support).
- FR-9: Background sync every 30 minutes by default (configurable: 15 min / 30 min / 1 hr / manual only), plus pull-to-refresh in UI. Respect Doze; document that exact timing is best-effort.
- FR-10: Handle recurring events correctly by syncing **expanded instances** (calendarView in Graph, `singleEvents=true` in Google) rather than parsing RRULEs locally. For ICS, use a well-tested library (e.g., Ical.Net) to expand recurrences.
- FR-11: Normalize all event times to UTC in storage; render in the device's local timezone. All-day events stored as date-only with an `isAllDay` flag (never converted through UTC).
- FR-12: Store per-event: source account, calendar ID, title, start/end, all-day flag, location, show-as/transparency status (busy/free/tentative/OOF), organizer, last-modified, and the provider's event ID for dedupe.
- FR-13: Deduplicate the same meeting appearing in both work accounts (matched by iCalUId when available, else fuzzy match on title + start + end) — show it once but badge it with both accounts.

### 5.3 Conflict Detection (Core Feature)

- FR-14: A **conflict** is two or more events from *any* calendars whose time ranges overlap, where both events have show-as status of `busy`, `tentative`, or `oof` (configurable; default excludes `free` events).
- FR-15: All-day events do NOT conflict with timed events by default (configurable toggle).
- FR-16: Detect conflicts within a single calendar too (double-booked in one work account), not just cross-account.
- FR-17: Conflict computation runs after every sync over the full synced window; results cached in SQLite.
- FR-18: Conflicts grouped into "conflict clusters" — if A overlaps B and B overlaps C, present as one cluster of 3.

### 5.4 UI

- FR-19: **Agenda view (default):** chronological unified list, each event color-coded by account (user-pickable colors), with a red conflict badge on conflicting events.
- FR-20: **Day and week views** with side-by-side overlap rendering (classic calendar layout where overlapping events split the column width).
- FR-21: **Conflicts tab:** dedicated list of conflict clusters, soonest first, showing date, time overlap range, and the involved events + their source accounts. This is the screen the user opens to plan manual fixes.
- FR-22: Tapping an event shows a read-only detail sheet (title, time, location, organizer, account, show-as status) with a deep-link button: "Open in Outlook" / "Open in Google Calendar" (launch the respective app via intent, falling back to web URL).
- FR-23: Optional home-screen widget: "Next conflict: <date/time>" + today's agenda. (Stretch goal — implement last.)
- FR-24: A persistent, subtle "Last synced: X min ago" indicator per account; error state shown per account if a sync fails.

### 5.5 Notifications (Optional, Phase 2)

- FR-25: Local notification when a *new* conflict appears within the next 7 days (diff against previous conflict set). Default off.

## 6. Non-Functional Requirements

- NFR-1 **Privacy:** No analytics, no crash reporting SDKs that ship event data, no third-party servers. Network traffic only to `graph.microsoft.com`, `login.microsoftonline.com`, `www.googleapis.com`, `accounts.google.com`, and user-supplied ICS hosts.
- NFR-2 **Data at rest:** app-private storage; enable SQLCipher only if low-effort, otherwise rely on Android app sandboxing + file-based encryption (acceptable for v1, document the decision).
- NFR-3 **Offline:** last-synced data fully viewable offline.
- NFR-4 **Performance:** unified agenda for a 67-day window must render in <500 ms from local DB on mid-range hardware.
- NFR-5 **No Play Store distribution assumed:** sideloaded APK / internal install. Keep signing simple.
- NFR-6 **Data wipe:** a settings action to disconnect an account must delete all its cached events and tokens immediately.

## 7. Data Model (suggested)

```
Account(id, type[MS|Google|ICS], displayName, email, color, lastSyncUtc, syncState/deltaToken, status)
CalendarSource(id, accountId, providerCalendarId, name, included)
EventInstance(id, calendarSourceId, providerEventId, iCalUId, title, startUtc, endUtc,
              isAllDay, showAs, location, organizer, lastModifiedUtc)
ConflictCluster(id, computedAtUtc)
ConflictMember(clusterId, eventInstanceId)
```

## 8. Edge Cases to Handle

1. Timezone-crossing events and DST transitions (test with events created in another timezone).
2. Recurring event exceptions (single instance moved/cancelled) — covered by instance-expansion approach, but write tests.
3. Identical meeting invited to both work addresses (dedupe, FR-13).
4. Declined events — exclude from conflict detection by default.
5. Multi-day timed events spanning midnight in week view rendering.
6. Token revocation by a tenant admin mid-use (graceful degradation, FR-4).
7. ICS feeds with missing `DTEND` (assume zero/default duration) or non-standard properties.
8. Graph delta token expiry (HTTP 410) → fall back to full window re-sync automatically.

## 9. Milestones

- **M1 — Spike:** consent pre-flight (§3) with both work tenants. Go/no-go per account, choose Graph vs ICS per tenant.
- **M2 — Sync core:** auth + one-way sync for all three accounts into SQLite; CLI-style debug dump of merged events.
- **M3 — Agenda + conflicts:** unified agenda view, conflict engine, conflicts tab.
- **M4 — Calendar views + polish:** day/week views, deep links, settings, per-account colors, data wipe.
- **M5 (optional):** widget + new-conflict notifications.

## 10. Acceptance Criteria (v1 done when…)

- [ ] All three accounts connected and syncing on a schedule without manual intervention for 1 week.
- [ ] A deliberately created overlap between Work-A and Google appears in the Conflicts tab within one sync cycle.
- [ ] A `free`-status event overlapping a `busy` event does NOT appear as a conflict.
- [ ] Recurring-event exception (one moved instance) shows the correct time, and the old slot shows no phantom event.
- [ ] App fully usable offline with last-synced data.
- [ ] Disconnecting an account removes its events and tokens.
