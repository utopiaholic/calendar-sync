# M1 Pre-Flight: Consent Check Results

Per spec §3, verify each work tenant allows Graph access via a personal app
registration before building. Fill in the results below as you complete each step.

## Step 1 — Register the Entra app (personal Azure account)

1. Sign in to https://entra.microsoft.com with your **personal** Azure account
   (create a free one if needed — no subscription required for app registrations).
2. **Identity → Applications → App registrations → New registration**
   - Name: `CalMerge`
   - Supported account types: **Accounts in any organizational directory (Any Microsoft Entra ID tenant - Multitenant)**
   - Redirect URI: platform **Mobile and desktop applications**, check the
     `https://login.microsoftonline.com/common/oauth2/nativeclient` box.
3. **API permissions → Add a permission → Microsoft Graph → Delegated:**
   `Calendars.Read`, `offline_access`, `User.Read` (User.Read is usually present by default).
4. **Authentication →** set **Allow public client flows** to **Yes**.
5. Copy the **Application (client) ID** here:

   - Client ID: `____________________________________`

> The Android platform entry (package name + signature hash) gets added in M2
> once the app project exists. The nativeclient redirect is enough for the
> browser consent test.

## Step 2 — Browser consent test (per work tenant, no code)

Build this URL, substituting your client ID (one line, no spaces):

```
https://login.microsoftonline.com/organizations/oauth2/v2.0/authorize?client_id=<CLIENT_ID>&response_type=code&redirect_uri=https%3A%2F%2Flogin.microsoftonline.com%2Fcommon%2Foauth2%2Fnativeclient&scope=Calendars.Read%20offline_access%20User.Read
```

Open it in an **incognito/private window** and sign in with each work account:

- A **consent screen** listing "Read your calendars" that you can Accept → **Graph: GO**.
  (Landing on a blank nativeclient page with `?code=...` in the URL afterwards confirms success.)
- **"Need admin approval"** dialog → Graph blocked. Either request approval
  (if your org's workflow allows and you're comfortable doing so) or mark NO-GO
  and do Step 3 for that tenant.

| Tenant | Account email | Consent result | Decision |
|---|---|---|---|
| Work A | | ☐ GO ☐ Admin approval needed ☐ NO-GO | ☐ Graph ☐ ICS |
| Work B | | ☐ GO ☐ Admin approval needed ☐ NO-GO | ☐ Graph ☐ ICS |

## Step 3 — ICS fallback (only for NO-GO tenants)

In **Outlook on the web** for that account:
**Settings (gear) → Calendar → Shared calendars → Publish a calendar** →
select the calendar, permission "Can view all details" → copy the **ICS** link.

- If publishing is available → **ICS fallback: GO**. Keep the URL private
  (it is an unauthenticated capability URL). Record only *that it exists* here,
  not the URL itself — you'll paste the URL into the app at onboarding.
- If publishing is disabled by policy too → that account **cannot be supported** (spec §3).

| Tenant | ICS publishing available? |
|---|---|
| Work A (if needed) | ☐ Yes ☐ No |
| Work B (if needed) | ☐ Yes ☐ No |

## Step 4 — Google Cloud project

1. https://console.cloud.google.com → create project `calmerge` (any name).
2. **APIs & Services → Library →** enable **Google Calendar API**.
3. **APIs & Services → OAuth consent screen:**
   - User type: **External**, Publishing status stays **Testing**.
   - Add `albertvincentreyes@gmail.com` under **Test users**.
   - Scopes: add `https://www.googleapis.com/auth/calendar.readonly` (non-sensitive scope entry is fine; no verification needed in Testing).
4. **Credentials → Create credentials → OAuth client ID → Android:**
   - Package name: `com.calmerge.app`
   - SHA-1: debug keystore fingerprint (captured in Phase 0 — see below).
5. Record:

   - Google OAuth client ID (Android): `____________________________________`

> Known limitation (FR-2): refresh tokens for Testing-mode apps expire after
> ~7 days. The app treats `invalid_grant` as a normal NEEDS_REAUTH state, not an error.

## Debug keystore SHA-1 (from Phase 0)

```
SHA-1: ____________________________________
```

## Outcome summary — RESOLVED 2026-06-11

**Graph consent is blocked for both work tenants. Decision: ICS feeds for ALL
THREE calendars, including Google** (via Calendar settings → "Secret address in
iCal format"). The app is built ICS-only; no Entra app, no Google Cloud
project, no OAuth code paths.

- Work A source type: **ICS** (Outlook Web → Settings → Calendar → Shared calendars → Publish)
- Work B source type: **ICS** (same)
- Google source type: **ICS** (Google Calendar → Settings → [calendar] → Integrate calendar → Secret address in iCal format)

Trade-offs accepted: no incremental sync (full re-download every refresh),
publisher-side update lag (Outlook published feeds can lag minutes to hours),
and no per-attendee response status from Google's ICS. Feed URLs are
unauthenticated capability URLs — entered only in the app, never committed.
