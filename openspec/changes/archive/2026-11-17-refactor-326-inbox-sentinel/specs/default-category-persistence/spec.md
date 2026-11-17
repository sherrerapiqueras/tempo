# Spec Delta

## ADDED Requirements

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
