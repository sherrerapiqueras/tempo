# Spec Delta

## Purpose

Gives every user-owned record a globally unique, stable identity and creation and modification timestamps. Backup import, deletion propagation and future sync can then recognise the same record across devices.

## ADDED Requirements

### Requirement: Core records carry a stable global identifier
Every category, task, task section, habit, habit chain, task attachment and saved focus session SHALL have an identifier that is a lowercase, canonical RFC 9562 UUID string. No two records of the same kind SHALL share one.

#### Scenario: New record gets an identifier
- **WHEN** the user creates a task
- **THEN** the stored task has a UUID identifier distinct from every other task's

#### Scenario: Identifiers differ across installs
- **WHEN** two separate installs each create a task
- **THEN** the two tasks have different identifiers, even if their local ids are equal

### Requirement: Identifiers never change
A record's identifier SHALL stay the same for the record's whole lifetime, across edits, completion, reordering, moves between categories or sections, and app restarts.

#### Scenario: Editing keeps the identifier
- **WHEN** the user edits a task's title, priority and category
- **THEN** the task's identifier is unchanged

#### Scenario: Habit completion keeps the identifier
- **WHEN** the user marks a habit complete for today
- **THEN** the habit's identifier is unchanged

### Requirement: Copies are new records
A record created by copying, duplicating or importing another SHALL receive its own new identifier. It SHALL NOT reuse the source's identifier.

#### Scenario: Recurring task spawns its next instance
- **WHEN** the user completes a recurring task and its next instance is created
- **THEN** the next instance has a different identifier from the completed one

#### Scenario: Attachments copied to another task
- **WHEN** attachments are copied from one task to another
- **THEN** each copied attachment has a new identifier

#### Scenario: Backup imported from a v1 file
- **WHEN** a backup file without identifiers is imported
- **THEN** every imported record receives a new identifier

### Requirement: Records carry creation and modification times
Each record with an identifier SHALL carry a creation time and a last-modified time as UTC epoch milliseconds. A newly created record SHALL have both set to its creation time.

#### Scenario: Times on creation
- **WHEN** a category is created
- **THEN** its creation and last-modified times are equal and equal to the time of creation

### Requirement: Modification time advances on every change
Any change to a record's stored fields SHALL advance its last-modified time to the time of the change, whichever feature makes it. The creation time SHALL NOT change.

#### Scenario: Toggling completion
- **WHEN** a task is marked complete
- **THEN** its last-modified time is the time of completion and its creation time is unchanged

#### Scenario: Reordering
- **WHEN** the user reorders tasks in a category
- **THEN** the last-modified time of every task whose order changed advances

### Requirement: Explicitly supplied times are preserved
When a write supplies a creation or last-modified time explicitly, for example when restoring or merging data from elsewhere, the system SHALL store that value and SHALL NOT replace it with the current time.

#### Scenario: Write with an explicit modification time
- **WHEN** a record is written with a last-modified time that differs from its stored one
- **THEN** the stored last-modified time equals the supplied value

### Requirement: Existing data gains identity on upgrade
Upgrading from a version without identifiers SHALL assign a unique identifier to every existing record of the covered kinds. It SHALL set creation times from existing creation dates where the record has one, and to the upgrade time otherwise. It SHALL set the last-modified time equal to the creation time. No other stored data SHALL change.

#### Scenario: Upgrade with existing tasks and habits
- **WHEN** a database containing tasks, habits and chains is upgraded
- **THEN** every row has a unique identifier, each habit's creation time matches its creation date, and all other field values are unchanged

### Requirement: Device-local records are excluded
Per-day focus statistics and habit-chain membership rows SHALL NOT carry their own identifier. Statistics are a property of the device. A membership is identified by its chain and its habit.

#### Scenario: Chain membership
- **WHEN** a habit is added to a chain
- **THEN** the membership is identifiable by the chain's and habit's identifiers alone
