# Spec Delta

## ADDED Requirements

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
