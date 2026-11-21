# Google Drive Backup — Cloud Setup

Drive backup (#306) and restore (#307) call the Drive API with an access token from Play services'
`AuthorizationClient`. Google issues that token only to an app registered in a Google Cloud
project, identified by its **package name and signing certificate**. No client ID or secret goes
into the app or the repository. Until this setup exists, turning on Drive backup shows "Couldn't
connect to Google Drive" (Play services returns `DEVELOPER_ERROR`).

## One-time setup

1. **Create a Google Cloud project** (or reuse one) at console.cloud.google.com.
2. **Enable the Google Drive API:** APIs & Services → Library → *Google Drive API* → Enable.
3. **Configure the OAuth consent screen** (Google Auth Platform → Branding / Audience / Data access):
   - user type: *External*;
   - app name *Tempo*, a support email, and the privacy policy URL;
   - data access, add the scope `https://www.googleapis.com/auth/drive.appdata`. It's listed as
     non-sensitive, so no verification is required ([#304](google-drive-backup-scopes.md));
   - Audience → publishing status: **In production**. With only a non-sensitive scope the app
     goes live without review, any Google account can connect, and authorisations don't expire
     after 7 days the way they do in *Testing*. Brand verification, which shows the app name and
     logo on the consent screen, is optional.
4. **Create Android OAuth clients** (Clients → Create client → *Android*), one per app identity:

   | Package name | SHA-1 from |
   |:---|:---|
   | `com.mandrecode.tempo.debug` | the debug keystore: `./gradlew :app:signingReport` (variant `debug`) |
   | `com.mandrecode.tempo` | **Play App Signing**: Play Console → Protect with Play → *App signing* (older consoles: Setup → App signing) → under *App signing key* (not *Upload key*), *SHA-1 certificate fingerprint* |

   Both are created here in Google Cloud; the Play Console only supplies the fingerprint. Add a third
   client with the upload key's SHA-1 if you install upload-signed builds directly.

## Checking it works

With a release build installed from Google Play, open Settings → Backup & Restore and turn on the
*Google Drive backup* switch. The consent screen should list "See, create, and delete its own
configuration data in your Google Drive". After you set the passphrase, tap the row to open the
Google Drive backup page: its status should show a backup within a minute on Wi-Fi.

To inspect what's stored, the files are in the hidden app data folder, which the Drive UI doesn't
show. Drive → Settings → Manage apps → Tempo shows its size and lets you delete it.
