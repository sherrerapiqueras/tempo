# Design

## Context

See proposal.md. The previous revision made the session sheet the one surface for setting up and running, with a saved session as a persisted, editable, deletable, linkable entity listed on Focus.

## Goals / Non-Goals

**Goals:** a list that limits itself; Focus's row about tasks; no management UI.

**Non-Goals:** keeping the saved-session entity's editing surface for later use.

## Decisions

### Recents are the same table, ordered by use and trimmed on write
`focus_sessions(id, title, focusMinutes, breakMinutes, rounds, lastUsedAt)`. `SavedSessionRepository.remember(session, now)` runs in one transaction: a row with the same title (trimmed, case-insensitive) is updated with the new numbers and time, otherwise one is inserted; then every row past the five most recently used is deleted. Trimming on write keeps the invariant in the data rather than in a query's `LIMIT`, so backups and merges cannot resurrect a longer list for long.

*Alternative:* a preferences-backed list. Rejected: the table, its migration and its backup support exist, and a running timer already refers to a row by id.

### A session is remembered when it actually starts
Start sends the draft. If something else is running the user is asked first, and the draft waits in `PendingStart`; only when the session really starts is it remembered and run. A session the user backed out of replacing leaves no trace.

### The setup state shrinks to "a new session is being set up"
With no existing session to open, `setupSessionId` only ever holds the new-session id or nothing. The sheet's latch keeps one hand-over: from the new session's stand-in key to the timer it started (`startedNewSessionId`).

### The task chip is a link, not a setting
`LinkedTaskRow` keeps the chip (name, summary line, notes mark) and its tap. Picking and clearing are removed with the linking they served.

### Unreleased schema is changed in place
Migration 13 → 14 is rewritten rather than followed by a 14 → 15: no released build has version 14. The owner agreed to clear the debug installs that do.

## Risks / Trade-offs

- [A topic unused while five others are used drops off] → accepted; pinning is the follow-up if it is missed.
- [Starting a topic is two taps instead of one] → accepted for a task-only row.
