# Spec Delta

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: A session is created with a name, a focus length, a rest and rounds
**Reason**: Sessions are no longer saved; a topic session is started and remembered by use.
**Migration**: See "A topic session is started from the add action".

### Requirement: Saved sessions are listed on Focus and reusable
**Reason**: Session cards crowded the day's tasks and grew without limit.
**Migration**: Recent topics are offered inside the new-session sheet.

### Requirement: A saved session can be edited and deleted
**Reason**: There is no saved session to edit or delete; recents manage themselves.
**Migration**: Start a topic with new values to update it; unused topics drop off.

### Requirement: A session can be linked to a task
**Reason**: A task already opens the same sheet with the same controls; a topic linked to a task duplicated the task's own card.
**Migration**: Start the session from the task.

### Requirement: Saved sessions are part of backups
**Reason**: Replaced by the backup rule for recent topic sessions.
**Migration**: See "Recent topic sessions are part of backups".

### Requirement: The linked task has one place and can always be taken off
**Reason**: With linking removed the task shown is the one the sheet was opened from, and it is not removable.
**Migration**: See "A session opened from a task shows that task as a link".
