# Proposal

Originating issue: [#517 — feat: create a focus session when in session screen](https://github.com/sherrerapiqueras/tempo/issues/517). Third revision, from the owner's use of the build.

## Why

Sessions are mainly a way to work on tasks; a session with no task is for a general topic — study, work — without having to invent a task for it. As built, every session started from the add button became a permanent card in the Up next row, competing with the day's tasks and growing without limit, and it could be linked to a task that already had a card of its own.

## What Changes

- **Topic sessions are remembered by use, not saved.** Starting a session from the add button just runs it. The last five topics used are kept as recents; using one moves it to the front and the oldest drops off. There is no save, edit or delete.
- **Recents live in the new-session sheet.** The sheet shows them as chips; tapping one fills in its name, focus, rest and rounds.
- **BREAKING (behaviour): Focus's row holds tasks only.** Session cards are removed from Up next.
- **BREAKING (behaviour): a topic session cannot be linked to a task.** A sheet opened from a task still shows that task as a chip that opens the task's own sheet; the chip can no longer be removed, and no task can be picked.
- The database table added by this PR changes shape before release: the task link goes and a last-used time is added.

### Non-goals

- Pinning a recent so it never drops off.
- Removing a single recent by hand.
- Any change to task timers, rounds, the session sheet's layout or the running card.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `focus-saved-sessions`: sessions become recents offered in the sheet; listing on Focus, editing, deleting and task links are removed.

## Impact

- **Data**: `focus_sessions` (still migration 13 → 14, unreleased) drops `taskId` and its index and gains `lastUsedAt`; the DAO orders by it and trims to five. Debug installs made from earlier commits of this PR need their data cleared.
- **Backup**: `focusSessions` entries drop `taskId` and gain `lastUsedAt`.
- **Domain**: remembering a session replaces saving one; the delete and linkable-task use cases go.
- **UI**: session cards, the footer, the delete dialog, the task picker and the unlink action are removed; a recents row is added to the sheet.
