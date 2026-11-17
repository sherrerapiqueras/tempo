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
| `tombstones` (#281) | **Never leaves the device**, but entries for rows the server already knows sync in 2.0 | The table records deletions from all seven identity tables, including deferred and local-only ones. Only a tombstone whose `uid` the server has already seen is uploaded (see [Tombstone provenance](#tombstone-provenance)), so a deletion in a shared list reaches the other members while nothing else does. |
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
Yes, but only for rows the server has already seen (see [Tombstone provenance](#tombstone-provenance)).
A tombstone records a deleted row's `uid`, its kind and the deletion time, and is kept for **180
days** on the device (#281). The server must keep it at least as long.

The window has to outlast the longest plausible gap between a member device's syncs. Otherwise
that device pushes back a row the others deleted. Half a year is far beyond normal use and still
covers a phone left in a drawer for a season.

Tombstones from completed-task retention clean-up are recorded like any other delete. Whether
they propagate to a shared list's members is a sharing decision.

### Tombstone provenance
A tombstone records only `uid`, kind and time, not which category the row was in. After a delete,
that category can't be recovered on the device, so the device can't decide from the tombstone alone
whether it belongs to a shared list. **The server's knowledge is the provenance instead.** The sync
engine must keep a record of every `uid` it has exchanged with the server, uploaded or received,
and push a tombstone only for a `uid` in that record. That rule:

- never leaks a deletion from an unshared category, or one from before a share, because the server
  never saw those rows;
- always propagates a deletion of a row the server knows, whatever caused it. Cascades are covered
  because each cascaded row gets its own tombstone, and its `uid` is in the record if the row was
  shared.

### Beyond the purge window
**The server must refuse an incremental sync from a device whose last successful sync is older
than the tombstone window.** It rejects the stale cursor, and any writes sent with it, and forces
a full resync. In a full resync the server's state wins for shared rows: a shared row the server
no longer has is dropped on the device, not uploaded again. Otherwise a device that was offline
past the window would bring back rows other members deleted after it last synced. The API
contract (#275) has to enforce this; it can't be left to clients.

### Unsharing
Unsharing stops future sync. The owner keeps their rows. What members keep is a sharing-design
decision (Phase 5), outside this document.

### Is the Inbox ever syncable or shareable?
**No.** The seeded Inbox carries a well-known `uid` that's identical on every install (#30), so
it can't be the identity of anyone's shared list. Clients don't offer "share" on it, and the API
contract has to reject any category with that `uid`. A user who wants to share their Inbox's tasks
moves them to another category and shares that.

## How the sync engine finds what to send

The device keeps a **pending-changes journal** (#546): one row per record that changed since it
was last acknowledged, with the record's `uid`, kind, latest operation (`upsert` or `delete`)
and a sequence number. The journal records every identity-bearing table, because it describes the
device, not what may leave it. **The sync engine applies this document when it pushes**, and the
rule differs by operation, because a deleted row's category is gone:

- **Upsert:** the live row still exists, so the engine checks it against "Syncs in 2.0" (a shared
  category, or a task or section inside one) and sends it only if it qualifies.
- **Delete:** the row and its category link are gone, so membership can't be read from it. The
  engine applies [Tombstone provenance](#tombstone-provenance) instead: it sends the delete only
  if the server already knows that `uid` for that kind. That covers every shared row, including
  cascades, and never an unshared or pre-share one.

It acknowledges each journal row it delivered, or decided not to send, by `uid`, kind and
sequence number, so a record edited during the upload stays pending.

## Changing this document

A change that makes anything new leave the device updates this document in the same PR, with
the reason. Reviews of sync, API and backup changes check against it.
