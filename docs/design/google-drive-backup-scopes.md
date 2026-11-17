# Google Drive Backup — OAuth Scope Decision

Outcome of the spike in #304 (part of #12). Checked against Google's documentation on
2026-11-17. Re-check the scope table before shipping, because Google reclassifies scopes from
time to time.

## Recommendation

**Build against `https://www.googleapis.com/auth/drive.appdata` only.** It is non-sensitive. No
security assessment, no demo video and no sensitive-scope review are needed. It also gives the
hidden, app-private folder that the feature was designed around.

Optional brand verification is free and lightweight, and it shows Tempo's name and logo on the
consent screen. Do it before release.

Keep `drive.file` as the documented fallback, not the primary design (see
[Fallback](#fallback-drivefile)).

## Scope classification

Source: [Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth).

| Scope | Tier | Notes |
|:---|:---|:---|
| `drive.appdata` (alias `drive.appfolder`) | **Non-sensitive** | "View and manage the app's own configuration data in your Google Drive." |
| `drive.file` | **Non-sensitive** | Per-file access to files the app created, or that the user opened with the app or picked. |
| `drive.apps.readonly` | Sensitive | Not needed. |
| `drive`, `drive.readonly`, `drive.metadata`, … | Restricted | Avoid. These need an annual CASA security assessment by a Google-empanelled assessor. |

The risk named in #12, that restricted scopes would need a paid annual assessment, **does not
apply**. Neither scope this feature needs is restricted.

## Verification obligations

Source: [OAuth app verification](https://support.google.com/cloud/answer/13463073),
[Sensitive scope verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification).

- **Non-sensitive only:** Google says that if an app "utilizes only non-sensitive scopes, it is
  not mandatory for your app to complete the app verification process."
- **Brand verification (recommended, free):** this is needed only to show an app name and logo on
  the consent screen. It requires:
  - an authorised domain verified in Search Console
  - a public homepage that describes the app
  - a privacy policy linked from both the homepage and the consent screen (`docs/PRIVACY_POLICY.md`
    exists, but it must be hosted and must mention Drive backup)

  Automated review usually finishes within minutes. Manual review takes about 2–3 business days.
- **Not required for this feature:** a demo video and per-scope justification (sensitive tier),
  and Limited Use plus CASA (restricted tier).
- **Cost:** none for non-sensitive scopes or brand verification. The docs do not publish CASA
  pricing, and it doesn't matter for this feature.

## Unverified-app behaviour and development

Source: [Unverified apps](https://support.google.com/cloud/answer/7454865),
[Manage app audience](https://support.google.com/cloud/answer/15549945).

- The "unverified app" warning screen and the 100-user lifetime cap are tied to unverified
  **sensitive or restricted** scopes. A non-sensitive-only app should trigger neither. The docs
  imply this but never state it outright, so confirm it on a real consent flow.
- **Testing publishing status:** this is limited to 100 listed test users, and **authorizations
  expire 7 days after consent**. Background backups during internal testing will therefore start
  failing after a week and need a fresh interactive grant. That is expected and is not a bug. It
  is also a free test of the "expired authorisation" failure path that #12 requires.
- Move the project to **In production** before any public release.

## The appDataFolder

Source: [Store application-specific data](https://developers.google.com/workspace/drive/api/guides/appdata).

- Only the app can see it. It is hidden from the user and from other Drive apps, and it is
  absent from the Drive UI.
- Files in it cannot be shared, moved between spaces, or trashed.
- **It is deleted when the user disconnects Tempo from their Drive, and users can delete it
  manually.** The backup UI must warn about this. It also means "last successful backup" must be
  read from Drive, not cached locally, because otherwise it would lie after a disconnect.
- Quota and size limits are not stated on that page. Assume the folder counts against the user's
  Drive storage, and surface quota errors as an actionable failure. Tempo backups are small
  (kilobytes to low megabytes), so size is not a design constraint.

## Android integration

Source: [Authorize access to Google user data](https://developer.android.com/identity/authorization).

- Use `AuthorizationClient` (`Identity.getAuthorizationClient(context).authorize(...)`) from
  `play-services-auth` to request `drive.appdata`. Authentication, if ever needed, goes through
  Credential Manager. Don't build on the legacy `GoogleSignIn` client.
- **Unattended backups:** after the first interactive grant, calling `authorize()` again
  returns an access token without UI for as long as the grant stands. A `WorkManager` job
  should:
  1. call `authorize()` silently
  2. if `hasResolution()` is false, use the token, which lives about an hour, and upload
  3. if `hasResolution()` is true, the grant has been revoked or has expired. Fail the job,
     record the cause as "authorisation needed", and show a notification or a settings banner
     that leads to the interactive flow. Never try to launch UI from the worker.
- **No refresh tokens on the device.** Google advises against storing them there, and offline
  access only makes sense with a backend. The silent-`authorize()` path needs neither, which
  keeps this feature independent of the 2.0 backend as #12 requires.
- The Google identity plumbing here (`AuthorizationClient`, the Cloud project and the consent
  screen) is shared with #292.

## Fallback: `drive.file`

This is viable but worse, so use it only if `drive.appdata` is ever reclassified.

- It is also non-sensitive, with the same light verification.
- Backups would be visible files in the user's Drive. That is arguably more transparent, but
  they clutter Drive and the user can move, rename or delete them.
- **Open risk:** it is not documented whether a fresh install on a new device can find files an
  earlier install created without the user picking them. Access is per file and per app, and
  restoring on a wiped device is an exit criterion for #12. If this fallback is ever needed,
  prototype restore on a second device first, and plan for a "pick your backup file" step that
  uses the Drive picker or SAF.

## Still to confirm by prototype

These are cheap to check during the implementation issues and are not blockers:

1. A `drive.appdata`-only consent flow on a production-status project shows no unverified-app
   screen.
2. Silent `authorize()` from a `WorkManager` worker returns a token without an Activity context.
   If it needs one, use the application context.
3. The disconnect path: what the Drive API returns after the user removes Tempo under Drive →
   Settings → Manage apps, and how that maps to an actionable error.

## Impact on related issues

- **#12:** proceeds as designed, using the hidden appDataFolder. No redesign is needed.
- **#305 (key model):** unaffected. The scope choice doesn't change the passphrase and Keystore
  problem.
- **#308 (SAF / Proton):** unaffected. It stays the non-Google option, and the SAF picker would
  double as the `drive.file` fallback's "pick your backup" step.
