# default-category-persistence Specification

## Purpose
Define how the default ("Inbox") category is seeded and how its name persists, so the default category is created exactly once and a user's rename is never silently overwritten by the localized default name on later launches or locale changes.

## Requirements

### Requirement: Default category is seeded once
The system SHALL create the default category with the current initial localized name only when it is seeded: on a fresh database, or when the database has no default category at all. It SHALL NOT create one while a default category exists.

#### Scenario: Fresh database creates default category
- **WHEN** the app creates a new database and seeds the default category
- **THEN** the stored default category name uses the current `category_inbox` string value

### Requirement: Default category name is user-owned after creation
The system SHALL persist user edits to the default category name and SHALL NOT overwrite that name during later app starts or database opens.

#### Scenario: Renamed default category survives restart
- **WHEN** a user renames the default category and the app database is opened again
- **THEN** the stored default category name remains the user-provided name

#### Scenario: Startup does not re-localize existing default category
- **WHEN** an existing default category row is opened after creation
- **THEN** the system does not replace its name with the current localized initial name

### Requirement: Default category identity is independent of display name
The system SHALL identify default category behavior using persisted identity/default metadata rather than the category name text.

#### Scenario: Renamed category remains default
- **WHEN** the default category has a custom user-provided name
- **THEN** the category remains the default category according to its persisted default metadata

### Requirement: Default category is restored when missing
Every time the database opens, the system SHALL ensure that a default category exists, creating the Inbox with its initial localized name only if none does. It SHALL NOT change any field of a category that already exists.

#### Scenario: Database that lost its categories gets the default back
- **WHEN** the database is opened and its categories table has no default category
- **THEN** the Inbox exists afterwards, is the default category, and the Tasks screen lists it

#### Scenario: Database reset by a schema downgrade gets the default back
- **WHEN** a debug build resets the database because its schema version is newer than the build's, and the database then opens
- **THEN** the Inbox exists and is the default category

#### Scenario: Existing default category is left alone
- **WHEN** the database is opened and a default category already exists, including a renamed or recoloured Inbox
- **THEN** no category is created and the existing one is unchanged

#### Scenario: Another default category was chosen
- **WHEN** the user made another category the default and then deleted the Inbox, and the database is opened again
- **THEN** the Inbox is not recreated and that other category stays the only default

### Requirement: Tasks are only created in an existing category
The system SHALL NOT create a task without an existing category. When no category exists, it SHALL NOT open the new-task editor and SHALL tell the user why, and the Tasks screen SHALL NOT treat a made-up category as selected.

#### Scenario: No category to add to
- **WHEN** categories have loaded, there are none, and the user asks to add a task
- **THEN** the editor does not open and a message says there is no category to put the task in

#### Scenario: Save without a real category
- **WHEN** the editor submits a task whose category is the "no category" placeholder
- **THEN** no task is created, the editor stays open with what the user typed, and a message says there is no category to put the task in

#### Scenario: Selection is not invented
- **WHEN** the Tasks screen loads with no categories
- **THEN** the selected category keeps its previous value instead of becoming a placeholder, and nothing is stored as the selection

### Requirement: Seeded Inbox has a well-known identifier
The Inbox category that the app seeds SHALL carry the same fixed identifier on every install, whether it was seeded on a fresh database, restored because no default existed, or present before the upgrade that introduced identifiers. Renaming the Inbox or making another category the default SHALL NOT change any category's identifier.

#### Scenario: Two installs
- **WHEN** two separate installs each have their seeded Inbox
- **THEN** both Inboxes have the same identifier

#### Scenario: Restored after going missing
- **WHEN** the default category is recreated because none existed
- **THEN** it has the well-known identifier

#### Scenario: Default moved to another category
- **WHEN** the user makes another category the default
- **THEN** the Inbox keeps the well-known identifier and the new default keeps its own

#### Scenario: Renamed default category
- **WHEN** the user renames the default category
- **THEN** its identifier is still the well-known one

### Requirement: Default category is not identified by a reserved id
The system SHALL NOT resolve the default category, or the seeded Inbox, by a reserved or negative local id. The default category SHALL be found by its default flag, and the seeded Inbox by its well-known identifier. A freshly seeded Inbox SHALL get an ordinary local id.

#### Scenario: Fresh install
- **WHEN** the database is created and the Inbox is seeded
- **THEN** the Inbox has a positive local id, the well-known identifier, and is the default category

#### Scenario: Existing install keeps working
- **WHEN** an install whose Inbox was seeded under the old reserved id starts
- **THEN** that Inbox is still found as the default category and nothing is re-seeded

#### Scenario: Restoring a legacy backup
- **WHEN** a version-1 backup whose Inbox carries the legacy reserved id is restored
- **THEN** the restored Inbox carries the well-known identifier

#### Scenario: Restoring a backup that already has a default
- **WHEN** a restored backup has a default category but no Inbox
- **THEN** no extra Inbox is created and the backup's default stays default
