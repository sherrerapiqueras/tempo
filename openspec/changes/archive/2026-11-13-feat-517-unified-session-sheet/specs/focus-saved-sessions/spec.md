# Spec Delta

## MODIFIED Requirements

### Requirement: A session is created with a name, a focus length, a rest and rounds
The session sheet opened for a new session SHALL ask for a name, a focus length, a rest length and a number of rounds, pre-filled with the configured session length, the configured break length and one round, around the same countdown ring a running session shows. Saving SHALL require a non-blank name. The sheet SHALL offer saving, and saving and starting at once.

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
Focus SHALL show saved sessions as cards in the Up next row, after the day's tasks, each with its name, its rest and rounds, and a start action. A session SHALL remain listed after it runs, until the user deletes it. The row SHALL be shown even when the day has no tasks.

#### Scenario: Session runs to the end
- **WHEN** a saved session finishes or is stopped
- **THEN** it is still listed on Focus and can be started again

#### Scenario: Empty day with sessions
- **WHEN** there are no tasks today or overdue but a saved session exists
- **THEN** Focus shows the sessions row rather than only the empty-day message

#### Scenario: Tasks and sessions together
- **WHEN** the day has tasks worth starting and saved sessions exist
- **THEN** one row holds the tasks first and the sessions after them

### Requirement: A saved session can be edited and deleted
Tapping a saved session's card SHALL open it in the session sheet with its values, where they can be changed and are saved when the sheet closes, or the session deleted after confirmation. Deleting a session that is running SHALL end its timer, keeping the minutes already earned.

#### Scenario: Edit
- **WHEN** the user opens "Study", changes the focus length to 50 minutes and saves
- **THEN** the card shows 50 minutes and the next start runs for 50 minutes

#### Scenario: Delete
- **WHEN** the user deletes a session and confirms
- **THEN** it is no longer listed on Focus

#### Scenario: Delete while running
- **WHEN** the user deletes the session whose timer is running
- **THEN** the timer ends and the minutes it earned stay in the day's focus time

## ADDED Requirements

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

### Requirement: The linked task has one place and can always be taken off
The session sheet SHALL show the task a session is for in the same row whether the sheet was opened from that task or the task was linked to a session, with the task's details beneath it. Before the session starts the row SHALL offer taking the link off; with no task it SHALL offer linking one where the sheet is setting up a session. Choosing a task SHALL show its details in place.

#### Scenario: Opened from a task
- **WHEN** the user opens the session sheet from a task
- **THEN** the task is shown in the linked-task row with its details beneath, as for a saved session linked to it

#### Scenario: Link taken off a sheet opened from a task
- **WHEN** the user takes the link off a sheet that was opened from a task
- **THEN** the same sheet becomes a new session with no task, offering to link one

#### Scenario: Linking loads the task
- **WHEN** the user links a task to a session being set up
- **THEN** the task's details appear under the row without leaving the sheet

#### Scenario: Running
- **WHEN** the session is running
- **THEN** the task is still shown and the link cannot be taken off

### Requirement: The linked task opens in its own sheet
Activating the linked task SHALL open that task's own sheet over the session sheet, without leaving the Focus tab. The session sheet SHALL NOT offer opening the task in the Tasks tab.

#### Scenario: Open the linked task
- **WHEN** the user taps the linked task on the session sheet
- **THEN** the task's sheet opens over it, and closing it returns to the session sheet

### Requirement: The session sheet is laid out as the app's other sheets are
The session sheet SHALL start-align its title, with the task's description beneath it, SHALL be as tall as its content up to the full height, and SHALL show the countdown inside a filled expressive shape with the time left traced round its edge, using a different shape while resting.

#### Scenario: Short session
- **WHEN** a session with no task is opened
- **THEN** the sheet is only as tall as its heading, timer, controls and buttons

#### Scenario: Resting
- **WHEN** a session is in its rest
- **THEN** the timer's shape differs from the one shown while focusing
