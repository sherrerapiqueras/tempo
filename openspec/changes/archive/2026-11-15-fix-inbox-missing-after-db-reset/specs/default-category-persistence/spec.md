# Spec Delta

## MODIFIED Requirements

### Requirement: Default category is seeded once
The system SHALL create the default category with the current initial localized name only when it is seeded: on a fresh database, or when the database has no default category at all. It SHALL NOT create one while a default category exists.

#### Scenario: Fresh database creates default category
- **WHEN** the app creates a new database and seeds the default category
- **THEN** the stored default category name uses the current `category_inbox` string value

## ADDED Requirements

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
