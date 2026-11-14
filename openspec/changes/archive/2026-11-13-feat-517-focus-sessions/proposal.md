# Proposal

Originating issue: [#517 — feat: create a focus session when in session screen](https://github.com/sherrerapiqueras/tempo/issues/517)

## Why

A focus timer can only be started on a task today, so "I just want to study for 25 minutes" means inventing a task first. A session should be something of its own: a name, a focus length, a rest and a number of rounds, which may be linked to a task or stand alone.

## What Changes

- **New entity: saved session.** A named, reusable session with a focus length, a rest length, a number of rounds and an optional linked task. Persisted, backed up, and shown only on Focus.
- **Sessions row on Focus.** Saved sessions get their own row of cards under Up next, each with a one-tap start. Tapping a card opens its editor; the editor can delete it.
- **BREAKING (behaviour): the Focus add button creates a session, not a task.** The floating bar's add action on Focus opens the new-session sheet. Tasks are still added from the Tasks tab. This replaces the behaviour shipped in #434.
- **Rounds.** A session with more than one round runs focus → rest → focus by itself until its rounds run out, telling the user at each change. A single-round session behaves like today's timer.
- **A session is not a task.** It never appears in the Tasks tab, has no checkbox, no date and no reminder, and does not count in the day's scheduled/completed progress. Its focus minutes do count in the day's focus time.
- **Optional task link.** A linked session shows which task it is for, banks its minutes against that task and offers Mark done for it. An unlinked session offers no task actions. Starting a timer straight from a task card keeps working as it does today.
- **Copy.** The undated-tasks action reads "Plan %d tasks" / "Planifica %d tareas" instead of "Give %d tasks a day" / "Ponle fecha a %d tareas".

### Non-goals

- No history of past session runs beyond the existing daily focus minutes.
- No change to how a timer started from a task card or a task reminder behaves.
- No docked (large-window) pane for the session editor; it is a sheet at every width.
- No reordering of saved sessions; they list in creation order.
- No settings for auto-starting rounds: a multi-round session always cycles by itself.

## Capabilities

### New Capabilities

- `focus-saved-sessions`: saved sessions as their own entity — creating, editing, deleting, listing on Focus, running with rounds, the optional task link, and their separation from tasks.

### Modified Capabilities

- `focus-add-task`: the Focus add action opens the new-session sheet instead of the blank task editor.

## Impact

- **Data**: new Room table `focus_sessions` (migration 13 → 14, schema JSON regenerated); backup file gains an additive `focusSessions` list (schema version unchanged).
- **Domain**: `features/focus/domain` — new model, repository and use cases; the running `FocusSession` gains an optional task and an optional round cycle.
- **Infrastructure**: session-end receiver advances rounds; ongoing notification and its actions handle a session with no task.
- **UI**: Focus content, running-session card, session sheet, finished sheet, new session editor sheet and task picker; floating bar add label on Focus.
- **Strings**: new session strings and the renamed plural in `values/` and `values-es/`.
- **Specs**: `focus-add-task` requirements renamed and rewritten.
