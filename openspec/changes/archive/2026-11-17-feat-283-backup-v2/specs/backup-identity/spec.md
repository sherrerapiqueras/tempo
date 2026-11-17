# Spec Delta

## Purpose

Lets a backup carry each record's stable identity so that restoring or merging it onto a device recognises records the device already has instead of duplicating or guessing.

## ADDED Requirements

### Requirement: Backups carry record identity
An exported backup SHALL be schema version 2 and SHALL include each category's, section's, task's, habit's, habit chain's and focus session's identifier, creation time and last-modified time.

#### Scenario: Export
- **WHEN** the user exports a backup
- **THEN** the file is version 2 and every such record carries its identifier and both times

### Requirement: Replace restores identity exactly
A replace import of a version-2 file SHALL restore every record under its identifier, creation time and last-modified time from the file.

#### Scenario: Export then replace
- **WHEN** a backup is exported and then restored with Replace
- **THEN** every restored record has the identifier and times it had when exported

### Requirement: Merge matches records by identity first
In a merge import, an incoming record whose identifier the device already has SHALL be treated as that record: it SHALL NOT be inserted, the device's version SHALL be kept, it SHALL NOT be reported as a conflict, and its children SHALL be matched on their own identifiers.

#### Scenario: Record renamed on the device since the backup
- **WHEN** a backup is merged that contains a task the device has since renamed
- **THEN** no new task is added, the device's renamed task is kept, and no conflict is reported

#### Scenario: Merging the same file twice
- **WHEN** a version-2 backup is merged onto a device and then merged again
- **THEN** the second merge adds nothing

### Requirement: Version-1 files still import
A version-1 file SHALL import as before, matching by natural keys on merge. Its records SHALL receive new identifiers, except that the category the file marks with the legacy Inbox id SHALL receive the seeded Inbox's well-known identifier.

#### Scenario: Restoring a version-1 file
- **WHEN** a version-1 backup is restored with Replace
- **THEN** all records import, each gets an identifier, and its Inbox has the well-known one

### Requirement: Unusable identities do not fail an import
An identifier in a file that is not a canonical UUID, or that an earlier record of the same kind in the file already used, SHALL be dropped, and that record SHALL import as a new record.

#### Scenario: Repeated identifier in an edited file
- **WHEN** two categories in a file carry the same identifier
- **THEN** the first keeps it, the second imports as a new category, and the import succeeds
