# Spec Delta

## Purpose

Keeps a bounded, ordered record of which records changed and how, so a future sync can replay local changes and know when each one has been delivered.

## ADDED Requirements

### Requirement: Every change to a record is journalled
Creating, editing or deleting a category, task, task section, habit, habit chain, task attachment or saved focus session SHALL leave a pending change for that record, holding its identifier, its kind and the operation (upsert or delete), whichever feature makes the change.

#### Scenario: Creating a task
- **WHEN** the user creates a task
- **THEN** a pending upsert exists for that task's identifier

#### Scenario: Deleting a category with tasks
- **WHEN** a category with tasks is deleted
- **THEN** a pending delete exists for the category and for each of its tasks

### Requirement: Pending changes coalesce per record
A record SHALL have at most one pending change, reflecting its latest operation.

#### Scenario: Edited many times
- **WHEN** a task is created and then edited five times
- **THEN** there is exactly one pending change for it, an upsert

#### Scenario: Created then deleted
- **WHEN** a task is created and then deleted
- **THEN** its one pending change is a delete

### Requirement: Pending changes are read in the order they happened
Pending changes SHALL be readable ordered by when each record last changed.

#### Scenario: Two records
- **WHEN** task A is edited and then task B is edited
- **THEN** reading pending changes returns A before B

### Requirement: Acknowledgement never loses a newer change
Acknowledging a pending change SHALL remove it only if the record has not changed again since the change was read. Otherwise it SHALL stay pending with its newer operation.

#### Scenario: Edited during an upload
- **WHEN** a pending change is read, the record is edited, and the read change is then acknowledged
- **THEN** the record still has a pending change

#### Scenario: Acknowledged unchanged
- **WHEN** a pending change is read and acknowledged without further edits
- **THEN** the record has no pending change

### Requirement: Upgrade starts with an empty journal
Upgrading to the version that introduces the journal SHALL keep all data unchanged and start with no pending changes.

#### Scenario: Upgrade
- **WHEN** a database with records is upgraded
- **THEN** all records are unchanged and there are no pending changes
