# Sync Boundary

What leaves the device in milestone 2.0, what is deferred, and what never leaves. Decided in
#278. This is the reference the API contract (#275) and any later change are checked against.
A field or table not listed here does not sync until this document says so.

**The rule:** cloud sync is optional, and in 2.0 it exists only to share lists. Nothing reaches
the server unless the user has explicitly shared the category it belongs to.

## The three buckets

| Bucket | Meaning |
|:---|:---|
| **Syncs in 2.0** | Uploaded and merged, but **only for categories the user has shared** |
| **Deferred to 3.0** | User data that will follow the user once full account sync exists. Stays on the device in 2.0 |
| **Never leaves the device** | A property of *this install*, not of the user. Doesn't sync even when full sync exists |

## Database (`TempoDatabase`)

| Table | Bucket | Reason |
|:---|:---|:---|
| `categories` | **Syncs in 2.0** (shared ones only) | A shared category *is* the shared list. Unshared categories never leave. |
| `tasks` | **Syncs in 2.0** (in shared categories only) | The content of a shared list. This includes subtasks, completed tasks and archived occurrences (see [Sharing an existing category](#sharing-an-existing-category)). |
| `task_sections` | **Syncs in 2.0** (in shared categories only) | Structure inside a shared list. Members would otherwise see tasks that point at sections they don't have. |
| tombstones of the three above (#281) | **Syncs in 2.0** | A deletion in a shared list has to reach the other members, or the row comes back on their next push. |
| `task_attachments` and their files | **Deferred to 3.0** | The files are binary and can be large (storage, quota and cost on the server), and nothing in sharing needs them. A shared task shows only the attachments its own device holds. |
| `habits`, `habit_chains`, `habit_chain_members` | **Deferred to 3.0** | Personal routines aren't shareable. `completionHistory` isn't mergeable yet (3.0 design work). |
| `focus_sessions` (saved sessions) | **Deferred to 3.0** | User data with no sharing purpose. |
| `daily_focus_activity` | **Never leaves the device** | Per-day statistics of what this device scheduled and finished. Merging two devices' days would double-count. Already excluded from stable identity (#30). |

Sync metadata (`uid`, `createdAt`, `updatedAt`, #30) travels with every row that syncs. Local
`id`s and foreign keys never leave the device. The sync layer translates them to `uid`s at its
boundary.

## Preferences (SharedPreferences)

"User" means a preference that should follow the user once 3.0 syncs settings. "Device" means it
describes this install and never syncs. Neither kind syncs in 2.0.

| Store (`file` → key) | Scope | Bucket | Reason |
|:---|:---|:---|:---|
| `theme_prefs` → `theme_mode` | User | Deferred to 3.0 | Light, dark or system is a personal choice. |
| `theme_prefs` → `use_tempo_colors` | Device | Never | It chooses between Tempo's palette and *this device's* wallpaper colours. |
| `app_prefs` → `app_language` | User | Deferred to 3.0 | The person's language, not the device's. |
| `completed_task_retention_prefs` → `enabled`, `retention_days` | User | Deferred to 3.0 | Decides what happens to the user's data, so two devices must agree on it. |
| `vacation_mode_prefs` → `periods` | User | Deferred to 3.0 | A vacation is a fact about the person. |
| `missed_reminder_prefs` → `enabled`, `catch_up_minute_of_day` | Device | Never | Tunes how *this device* catches up on reminders it couldn't deliver. |
| `tasks_screen_prefs` → selected category, collapsed sections, show completed, sort options | Device | Never | View state. It also holds local category and section ids, which mean nothing elsewhere. |
| `navigation_prefs` → `last_route` | Device | Never | Where this install was last. |
| `onboarding_preferences` → `completed` | Device | Never | A new install should still introduce itself. |
| `focus_session_prefs` → running timer, session counts for today, default lengths | Device | Never | A running timer belongs to the device it runs on. Today's counts feed `daily_focus_activity`. |
| `active_live_activity_prefs` → `active_chain_ids` | Device | Never | Which ongoing notifications this device is showing. |
| Backup settings and reminders (`BackupSettingsDataSource`) | Device | Never | Each device backs itself up. Drive backup (#12) is per install. |
| `tempo_secure_prefs` → `db_passphrase_blob` | Device | **Never, under any circumstances** | The Keystore-wrapped database key. Moving it would defeat database encryption. #291 governs cloud secrets separately. |
| `tempo_db_kdf_prefs` → `db_kdf_iter` | Device | Never | Describes how this file is keyed. |

## Other device state

| State | Bucket | Reason |
|:---|:---|:---|
| Android Keystore keys (`tempo_db_passphrase_key`) | Never | Hardware-bound, and can't leave by design (`docs/DB_ENCRYPTION.md`). |
| Notification, exact-alarm and other runtime permission state | Never | Owned by the OS, per install. |
| Scheduled alarms and WorkManager jobs | Never | Derived from data on each device. Every device schedules its own from what it holds. |
| Home-screen widget placement and configuration | Never | Owned by the launcher, per device. |

## Questions this document answers

### Does a task in a non-shared category ever reach the server in 2.0?
**No.** That covers the title, any metadata, its existence and its tombstone. The server can't tell
how many unshared categories or tasks a user has.

### Sharing an existing category
When a category is shared, its **current live rows** upload. That means the category, its sections
and all its tasks, including completed tasks and archived occurrences of recurring tasks. The
completed history is part of the list the user chose to share, and leaving it out would make
members' views disagree with the owner's.

**Tombstones from before the share don't upload.** The server never had those rows, so there's
nothing to propagate. Rows the completed-task retention setting has already removed are gone and
don't upload either.

### Are tombstones synced, and for how long?
Yes, for rows in shared categories, once the server has seen the row. A tombstone stays on device
and server until the purge window in #281 ends. That window has to outlast the longest plausible
offline period of a member device. After the purge, a device that was offline longer than the window
may push the row back. #281 documents the window and why it was chosen.

### Unsharing
Unsharing stops future sync. The owner keeps their rows. What members keep is a sharing-design
decision (Phase 5), outside this document.

### Is the Inbox ever syncable or shareable?
**No.** The seeded Inbox carries a well-known `uid` that's identical on every install (#30), so
it can't be the identity of anyone's shared list. Clients don't offer "share" on it, and the API
contract has to reject any category with that `uid`. A user who wants to share their Inbox's tasks
moves them to another category and shares that.

## Changing this document

A change that makes anything new leave the device updates this document in the same PR, with
the reason. Reviews of sync, API and backup changes check against it.
