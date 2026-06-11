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

## Status

- **M1 (consent pre-flight):** done — ICS for all three sources.
- **M2 (sync core):** ICS engine, Room storage, recurrence expansion via
  biweekly, cross-feed dedupe, WorkManager background sync, debug merged-events
  screen.
- M3+ (agenda view, conflict engine, calendar views): not started.
