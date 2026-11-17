# deletion-tombstones Specification

## Purpose
Makes deletions of records with a stable identity durable facts, so a later sync can propagate them instead of resurrecting the record, without ever showing a deleted record in the app.

## Requirements

### Requirement: Deleting a record leaves a tombstone
Deleting any category, task, task section, habit, habit chain, task attachment or saved focus session SHALL record a tombstone holding the record's identifier, its kind and the time of deletion, whichever feature performs the deletion.

#### Scenario: Deleting a task
- **WHEN** the user deletes a task
- **THEN** a tombstone with that task's identifier, kind "task" and the deletion time exists

#### Scenario: Bulk delete
- **WHEN** completed tasks are cleared from a category
- **THEN** every removed task has its own tombstone

### Requirement: Deleted records never appear as live data
A deleted record SHALL NOT be returned by any query that reads live records. Only the tombstone remains.

#### Scenario: Reading after delete
- **WHEN** a category is deleted
- **THEN** it does not appear in the category list, and looking it up by id finds nothing

### Requirement: Cascaded deletions are recorded individually
When deleting a record also deletes others, each deleted record SHALL get its own tombstone.

#### Scenario: Deleting a category with tasks and sections
- **WHEN** a category containing tasks and sections is deleted
- **THEN** the category, each of its tasks and each of its sections has a tombstone

#### Scenario: Deleting a task with subtasks
- **WHEN** a task with nested subtasks is deleted
- **THEN** the task and every subtask in its tree has a tombstone

### Requirement: Restoring a record clears its tombstone
When a deleted record is restored under its original identifier, its tombstone SHALL be removed. What the user sees after undo SHALL be unchanged from before this capability.

#### Scenario: Undo after delete
- **WHEN** the user deletes a task and taps Undo
- **THEN** the task is back with its original identifier, and no tombstone exists for it

### Requirement: Tombstones are purged after 180 days
Tombstones older than 180 days SHALL be removed. Younger ones SHALL be kept.

#### Scenario: Old tombstone at startup
- **WHEN** the database opens and a tombstone is 181 days old
- **THEN** that tombstone is removed and one that is 10 days old is kept

### Requirement: Upgrade keeps existing data
Upgrading to the version that introduces tombstones SHALL keep every existing record unchanged and start with no tombstones.

#### Scenario: Upgrade from the previous version
- **WHEN** a database with categories, tasks and habits is upgraded
- **THEN** all records are unchanged and the tombstone list is empty
