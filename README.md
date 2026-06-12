# CalMerge (calendar-sync)

A privacy-first, read-only Android app that merges three calendars — two work
Outlook calendars and one personal Google calendar — into a single unified view
and surfaces scheduling conflicts. See `calendar-aggregator-spec.md` for the
full requirements.

## Source type: ICS-only

The M1 pre-flight (see `docs/preflight-results.md`) found that **Microsoft Graph
consent is blocked in both work tenants**, so per spec §3 all three calendars
are consumed as **published ICS feeds**:

- Work A / Work B: Outlook Web → Settings → Calendar → Shared calendars → Publish a calendar
- Google: Calendar settings → Integrate calendar → *Secret address in iCal format*

There are no OAuth flows, no app registrations, and no tokens — the app's only
network traffic is HTTPS GETs to the three feed URLs. Feeds are fully
re-downloaded and re-parsed on each sync (every 30 min via WorkManager, plus
manual sync).

## Build

Requires Android SDK 35. `local.properties` must point `sdk.dir` at the SDK.

```
./gradlew assembleDebug   # build APK
./gradlew test            # JVM unit tests (window math, ICS parsing, dedupe)
```

## Features

- **Tab-based navigation:** Home, Agenda, Calendar, and Conflicts tabs
- **Home screen:** greeting, daily summary (events + conflict count), feed management, next conflict preview, today's events, sync controls, and settings
- **Agenda view:** chronological unified event list across all feeds, color-coded per calendar, conflict badges, filter by week or all events
- **Calendar views:** day and week grid layouts (FR-20), time-based event blocks with account colors, conflict indicators, all-day event strip
- **Conflicts timeline:** conflict clusters sorted by occurrence (FR-21), configurable detection (tentative, OOF, all-day vs. timed events)
- **Feed management:** add/remove ICS feeds, per-calendar color customization, toggle feeds on/off
- **Settings:** sync interval (15/30/60 min or manual), conflict detection thresholds, data wipe
- **Sync engine:** ICS parsing with recurrence expansion, Room database for event storage, cross-feed deduplication, WorkManager background sync
- **Conflict detection:** sweep-line algorithm for overlapping events, configurable filtering rules

## Status

- **M1 (consent pre-flight):** done — ICS for all three sources
- **M2 (sync core):** done — ICS engine, Room storage, recurrence expansion, cross-feed dedupe, WorkManager background sync
- **M3 (UI):** done — agenda, calendar grid, and conflict timeline views shipped
- **Current:** UX improvements in progress — date-aware views and configurable conflict detection window
