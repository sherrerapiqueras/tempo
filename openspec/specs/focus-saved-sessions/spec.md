# focus-saved-sessions Specification

## Purpose
Defines saved focus sessions: named, reusable timers with a focus length, a rest, rounds and an optional linked task, which live on Focus and are never treated as tasks.

## Requirements

### Requirement: A session is not a task
A saved session SHALL NOT appear in the Tasks tab, SHALL have no completion state, date or reminder, and SHALL NOT count in the day's scheduled or completed totals. Focus minutes earned by a session SHALL count in the day's focus time.

#### Scenario: Tasks tab
- **WHEN** saved sessions exist
- **THEN** the Tasks tab shows none of them

#### Scenario: Day progress
- **WHEN** a saved session finishes on a day with two tasks, one completed
- **THEN** the day's progress still reads one of two, and its focus minutes include the session's

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

### Requirement: The undated-tasks action speaks of planning
The Focus action that opens the planning sheet SHALL be labelled as planning the undated tasks, with their count.

#### Scenario: Three undated tasks
- **WHEN** three open tasks have no date
- **THEN** the action reads "Plan 3 tasks" in English and "Planifica 3 tareas" in Spanish

### Requirement: One sheet sets a session up and runs it
A new session, a saved session and a task SHALL all open the same session sheet. Starting from it SHALL keep that sheet open and change it into the running timer, with the change animated; it SHALL NOT close one sheet and open another.

#### Scenario: Start a saved session from its sheet
- **WHEN** the user opens a saved session and presses start
- **THEN** the same sheet shows its countdown running, and the setup controls give way to the running controls

#### Scenario: Start a new session from its sheet
- **WHEN** the user names a new session and presses start
- **THEN** the sheet stays open on that session's running timer

### Requirement: Pending rounds are shown
A session with more than one round SHALL show one marker per round on its sheet and on its running card, telling apart the rounds done, the one under way and those still to come.

#### Scenario: Second of four
- **WHEN** a four-round session is in its second round
- **THEN** one round reads as done, one as under way and two as still to come

#### Scenario: Single round
- **WHEN** a session has one round
- **THEN** no round markers are shown

### Requirement: A task timer can be given a rest and rounds
The session sheet opened on a task SHALL offer the same focus, rest and rounds controls a session has, applying to that run alone. A task run with more than one round SHALL cycle focus and rest by itself as a saved session does, and SHALL NOT create a saved session.

#### Scenario: Two rounds on a task
- **WHEN** the user opens a task, sets two rounds and starts
- **THEN** the timer runs focus, rest and focus on that task, and no session is added to Focus

#### Scenario: Defaults
- **WHEN** the user opens a task and presses start without changing anything
- **THEN** a single session of the configured length starts, as before

### Requirement: The linked task opens in its own sheet
Activating the linked task SHALL open that task's own sheet over the session sheet, without leaving the Focus tab. The session sheet SHALL NOT offer opening the task in the Tasks tab.

#### Scenario: Open the linked task
- **WHEN** the user taps the linked task on the session sheet
- **THEN** the task's sheet opens over it, and closing it returns to the session sheet

### Requirement: The session sheet is laid out as the app's other sheets are
The session sheet SHALL start-align its title, with the session's status beneath it, and show the countdown beside the title inside a filled expressive shape with the time left traced round its edge, using a different shape while resting. The linked task SHALL be a row among the sheet's other rows, shown as a chip carrying the task's name and, beneath it, its category, priority and due day and time. The sheet SHALL be as tall as its content up to the full height.

#### Scenario: Short session
- **WHEN** a session with no task is opened
- **THEN** the sheet is only as tall as its heading, timer, controls and buttons

#### Scenario: Resting
- **WHEN** a session is in its rest
- **THEN** the timer's shape differs from the one shown while focusing

### Requirement: A linked task's description is marked, not shown
The session sheet SHALL NOT write out the linked task's description. When the linked task has a description, its chip SHALL carry a mark saying so, the same before and after the session starts; the description is read in the task's own sheet.

#### Scenario: Task with a description
- **WHEN** a session is linked to a task that has a description
- **THEN** the chip shows the mark and the description's text appears nowhere on the session sheet

#### Scenario: Task without one
- **WHEN** the linked task has no description
- **THEN** the chip shows no such mark

#### Scenario: Starting
- **WHEN** the session starts
- **THEN** nothing about the task's description appears or disappears

### Requirement: A topic session is started from the add action
The session sheet opened from Focus's add action SHALL ask for a name, a focus length, a rest length and a number of rounds, pre-filled with the configured session length, the configured break length and one round, and SHALL offer starting it. Starting SHALL require a non-blank name. The sheet SHALL NOT offer saving, editing or deleting a session.

#### Scenario: Start a topic
- **WHEN** the user enters "Study" and starts
- **THEN** a session named "Study" runs at the configured lengths, in the same sheet

#### Scenario: Blank name
- **WHEN** the user starts with an empty name
- **THEN** nothing starts and the sheet asks for a name

#### Scenario: Closed without starting
- **WHEN** the user closes the sheet without starting
- **THEN** no session runs and nothing is remembered

### Requirement: Recent topic sessions are offered in the sheet
The new-session sheet SHALL offer the topic sessions most recently started, most recent first, at most five. Choosing one SHALL fill in its name, focus length, rest length and rounds. Starting a session whose name matches a recent one SHALL update that one rather than add another. When a sixth distinct topic is started, the least recently used SHALL no longer be offered.

#### Scenario: Reuse a topic
- **WHEN** the user opens the sheet, chooses "Study" from the recents and starts
- **THEN** the session runs with the lengths and rounds "Study" last ran with, and "Study" is first among the recents

#### Scenario: Same name again
- **WHEN** the user starts "study" having started "Study" before
- **THEN** there is still one such recent, with the latest lengths and rounds

#### Scenario: Sixth topic
- **WHEN** five topics are remembered and a sixth is started
- **THEN** five are offered, and the one used longest ago is not among them

#### Scenario: None yet
- **WHEN** no topic session has been started
- **THEN** the sheet shows no recents

### Requirement: Focus lists tasks, not sessions
Focus's Up next row SHALL hold the running session's card and the day's tasks worth starting, and SHALL NOT hold cards for topic sessions.

#### Scenario: Topics remembered
- **WHEN** topic sessions have been started before and none is running
- **THEN** Up next shows only tasks

### Requirement: A session opened from a task shows that task as a link
The session sheet opened from a task, or running on a task, SHALL show the task as a chip with its details. The chip SHALL open the task's own sheet. The sheet SHALL NOT offer removing the task or choosing another, and a topic session SHALL NOT show a task.

#### Scenario: Task session
- **WHEN** the session sheet is opened from a task
- **THEN** the task's chip is shown, with no way to remove it

#### Scenario: Topic session
- **WHEN** the session sheet is opened from the add action
- **THEN** no task row is shown

### Requirement: Recent topic sessions are part of backups
A backup SHALL carry the remembered topic sessions with when each was last used. A full restore SHALL bring them back; a merge SHALL add those whose name the device does not already have. No more than five SHALL remain after either. Backups written before sessions existed SHALL still import.

#### Scenario: Restore
- **WHEN** a backup holding three topic sessions is restored in full
- **THEN** the sheet offers those three, in the order they were last used

#### Scenario: Older backup
- **WHEN** a backup written before this change is imported
- **THEN** the import succeeds and no sessions are added
