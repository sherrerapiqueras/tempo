# Privacy Policy for Tempo

**Last Updated: November 21, 2026**

This Privacy Policy describes how your personal information is handled in the **Tempo** mobile
application ("the App").

### 1. Data Collection and Usage

The App is designed with a "privacy-first" and "local-first" approach.

* **Personal Data:** We do not collect, store, or transmit any personal data (such as your name,
  email address, or phone number) to our servers or any third parties. We do not operate any
  servers that receive your data.
* **User Content:** All tasks, habits, categories, and routines you create are stored **locally on
  your device** using a Room database. The App does not transmit this data to us. The only time it
  leaves your device is when **you** turn on the optional Google Drive backup described in
  [section 2](#2-google-drive-backup-optional), which sends it, encrypted, to your own Google
  Drive. Android may include the App's data in a system backup when backups are enabled on your
  device; those backups are governed by your operating system and backup-provider settings.
* **Analytics and Tracking:** The App does not use any third-party analytics, tracking, or
  advertising SDKs.

### 2. Google Drive backup (optional)

The App can back up your data to your own Google Drive. It is **off by default**, and nothing is
sent to Google unless you turn it on in Settings → Backup & Restore → Google Drive backup, or choose
to restore from Google Drive during setup.

* **What the App can access:** only the `drive.appdata` permission, which gives the App a private,
  hidden folder in your Google Drive. The App **cannot see, open, or change any of your other Drive
  files**, and the folder does not appear in the Drive interface. Google shows you this permission
  and asks for your consent before anything is sent. The App does not request your name, email
  address, or profile, and does not read them.
* **What is stored there:** encrypted backup files of your tasks, habits, categories, routines, and
  settings. Each file is encrypted **on your device, before upload**, with AES-256-GCM using a key
  derived from a backup passphrase that you choose. File names contain a random identifier for
  your installation and the time of the backup; they contain no personal information. The App
  keeps the five most recent backups for each device and deletes older ones.
* **Who can read it:** only someone who knows your backup passphrase. Google stores the files but
  cannot decrypt them, and we never receive them: the data goes directly from your device to your
  Google Drive. Google's handling of the stored files is covered by
  [Google's Privacy Policy](https://policies.google.com/privacy).
* **Your passphrase:** stored only on your device, encrypted with a key held by the Android
  Keystore, so scheduled backups can run without asking you. It is never uploaded and is excluded
  from Android's own device backups. We cannot recover it: if you lose it, your Drive backups
  cannot be restored.
* **When it is used:** to make the scheduled backups you configure (daily or weekly, optionally
  only on Wi-Fi), the backups you start yourself, and restores you ask for.
* **Turning it off and deleting backups:** turning Google Drive backup off in the App stops new
  backups and erases the passphrase from your device; backups already in Drive stay there until you
  delete them. To delete them, open Google Drive → Settings → Manage apps → Tempo → Delete hidden
  app data. Removing Tempo's access to your Google Account
  ([myaccount.google.com/connections](https://myaccount.google.com/connections)) also deletes the
  folder.

Tempo's use and transfer of information received from Google APIs adheres to the
[Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
including the Limited Use requirements.

### 3. Fonts

The App includes Google Sans Flex font files within the application package. It does not connect to
Google's servers to download fonts at runtime.

### 4. Permissions

The App requests the following permissions to function correctly:

* **`android.permission.INTERNET`:** Used only for the optional Google Drive backup and restore.
  The App makes no other network connections.
* **`android.permission.POST_NOTIFICATIONS`:** Allows the App to display task and habit reminders
  on Android 13 and above.
* **`android.permission.SCHEDULE_EXACT_ALARM`:** Allows the App to deliver reminders at the times
  you schedule.
* **`android.permission.POST_PROMOTED_NOTIFICATIONS`:** Allows supported Android versions to present
  eligible reminder notifications more prominently.
* **`android.permission.RECEIVE_BOOT_COMPLETED`:** Allows the App to restore scheduled reminders
  after your device restarts.

### 5. Data Security

* **Local database:** Your tasks, habits, and routines are stored in an encrypted local database
  (SQLCipher). The encryption key is generated on your device and protected by the Android
  Keystore; it never leaves your device and you do not need to set or remember a password for
  everyday use of the app.
* **Backup exports:** When you export a backup (Settings → Backup → Export data), the file is
  encrypted with a passphrase you choose at export time. You must remember this passphrase to
  restore the backup later — we cannot recover it or decrypt the file if it's lost, since we
  never see or store it.
* **Google Drive backups:** encrypted on your device with your backup passphrase before they are
  uploaded, as described in [section 2](#2-google-drive-backup-optional).
* Because your data is stored locally on your device, its overall security also depends on the
  security of your device itself. We recommend using device-level security features such as
  PINs, passwords, or biometric authentication to protect your information.

### 6. Changes to This Privacy Policy

We may update our Privacy Policy from time to time. We will notify you of any changes by posting the
new Privacy Policy within the App or on our official website/listing.

### 7. Contact Us

If you have any questions or suggestions about our Privacy Policy, do not hesitate to contact us at:
mandrecode@gmail.com
