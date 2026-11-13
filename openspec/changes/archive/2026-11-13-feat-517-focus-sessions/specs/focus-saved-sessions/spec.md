# Spec Delta

## Purpose

Defines saved focus sessions: named, reusable timers with a focus length, a rest, rounds and an optional linked task, which live on Focus and are never treated as tasks.

## ADDED Requirements

### Requirement: A session is created with a name, a focus length, a rest and rounds
The new-session sheet SHALL ask for a name, a focus length, a rest length and a number of rounds, pre-filled with the configured session length, the configured break length and one round. Saving SHALL require a non-blank name. The sheet SHALL offer saving, and saving and starting at once.

#### Scenario: Create a session
- **WHEN** the user enters "Study", leaves the defaults and saves
- **THEN** a session named "Study" exists with the configured focus and break lengths and one round

#### Scenario: Blank name
- **WHEN** the name is empty or only spaces
- **THEN** the sheet does not offer saving

#### Scenario: Save and start
- **WHEN** the user fills the sheet and chooses to start
- **THEN** the session is saved and its timer starts at once

### Requirement: Saved sessions are listed on Focus and reusable
Focus SHALL show saved sessions in their own row, in creation order, each with its name, its lengths and rounds, and a start action. A session SHALL remain listed after it runs, until the user deletes it. The row SHALL be shown even when the day has no tasks.

#### Scenario: Session runs to the end
- **WHEN** a saved session finishes or is stopped
- **THEN** it is still listed on Focus and can be started again

#### Scenario: Empty day with sessions
- **WHEN** there are no tasks today or overdue but a saved session exists
- **THEN** Focus shows the sessions row rather than only the empty-day message

### Requirement: A saved session can be edited and deleted
Tapping a saved session's card SHALL open it in the session sheet with its values, where they can be changed and saved, or the session deleted after confirmation. Deleting a session that is running SHALL end its timer, keeping the minutes already earned.

#### Scenario: Edit
- **WHEN** the user opens "Study", changes the focus length to 50 minutes and saves
- **THEN** the card shows 50 minutes and the next start runs for 50 minutes

#### Scenario: Delete
- **WHEN** the user deletes a session and confirms
- **THEN** it is no longer listed on Focus

#### Scenario: Delete while running
- **WHEN** the user deletes the session whose timer is running
- **THEN** the timer ends and the minutes it earned stay in the day's focus time

### Requirement: A session is not a task
A saved session SHALL NOT appear in the Tasks tab, SHALL have no completion state, date or reminder, and SHALL NOT count in the day's scheduled or completed totals. Focus minutes earned by a session SHALL count in the day's focus time.

#### Scenario: Tasks tab
- **WHEN** saved sessions exist
- **THEN** the Tasks tab shows none of them

#### Scenario: Day progress
- **WHEN** a saved session finishes on a day with two tasks, one completed
- **THEN** the day's progress still reads one of two, and its focus minutes include the session's

### Requirement: A session can be linked to a task
The session sheet SHALL offer an optional link to one open task, which can be changed or removed. A running linked session SHALL bank its minutes against that task and offer marking the task done; an unlinked session SHALL offer no task actions. Deleting the task SHALL leave the session, unlinked.

#### Scenario: Linked session offers the task
- **WHEN** a session linked to "Read chapter 3" is running
- **THEN** its controls offer Mark done, which completes "Read chapter 3" and ends the timer

#### Scenario: Unlinked session
- **WHEN** a session with no linked task is running
- **THEN** its card, its sheet and its notification offer no Mark done action

#### Scenario: Linked task deleted
- **WHEN** the task a session is linked to is deleted
- **THEN** the session is still listed, with no link

### Requirement: Rounds cycle by themselves
A session with more than one round SHALL, when a focus round's time runs out, start its rest by itself, and when the rest runs out start the next focus round, until the last focus round ends. Each change SHALL notify the user. The running session SHALL show which round it is on. No rest SHALL follow the last round by itself.

#### Scenario: Two rounds
- **WHEN** a session of two rounds of 25 minutes with a 5 minute rest is started and left alone
- **THEN** it runs 25 minutes of focus, 5 of rest and 25 of focus, and then finishes

#### Scenario: One round
- **WHEN** a session of one round runs out
- **THEN** it finishes and the user is offered what to do next, as for a task timer

#### Scenario: Rest cut short
- **WHEN** the user chooses to go back to work during a rest between rounds
- **THEN** the next focus round starts at once

#### Scenario: Stopped mid-way
- **WHEN** the user stops the session during any round
- **THEN** no further round or rest starts

### Requirement: Starting a session respects the running timer
Starting a saved session while a focus timer on something else is running SHALL ask before replacing it, as starting on another task does. Starting while a break is running SHALL NOT ask.

#### Scenario: Another timer is running
- **WHEN** a task timer is running and the user starts a saved session
- **THEN** the user is asked to confirm, and confirming ends the task timer with its earned minutes banked

### Requirement: Saved sessions are part of backups
A backup SHALL carry saved sessions. A full restore SHALL bring them back with their task links; a merge SHALL add the sessions the device does not already have, linked to the matching task where it came across and unlinked otherwise. Backups written before sessions existed SHALL still import.

#### Scenario: Restore
- **WHEN** a backup holding a session linked to a task is restored in full
- **THEN** the session is listed on Focus, linked to that task

#### Scenario: Older backup
- **WHEN** a backup written before this change is imported
- **THEN** the import succeeds and no sessions are added

### Requirement: The undated-tasks action speaks of planning
The Focus action that opens the planning sheet SHALL be labelled as planning the undated tasks, with their count.

#### Scenario: Three undated tasks
- **WHEN** three open tasks have no date
- **THEN** the action reads "Plan 3 tasks" in English and "Planifica 3 tareas" in Spanish
