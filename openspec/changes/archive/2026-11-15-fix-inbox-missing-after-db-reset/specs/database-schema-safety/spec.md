# Spec Delta

## Purpose

Defines what the app does when the database file's schema version does not match the installed build, so a mismatch never silently deletes user data in a release build.

## ADDED Requirements

### Requirement: Release builds never discard data on a schema mismatch
A release build SHALL NOT delete or recreate the database when its schema version is newer than the build's, or when no migration path to the build's version exists. It SHALL fail to open the database and leave the file as it was.

#### Scenario: Release build opens a newer database
- **WHEN** a release build opens a database whose schema version is higher than the build's
- **THEN** opening fails with an error, and the database file and its data are unchanged

#### Scenario: Matching version is restored
- **WHEN** a build with the newer schema is installed again after such a failure
- **THEN** the data is there and the app opens normally

### Requirement: Debug builds reset on downgrade only
A debug build SHALL reset the database, dropping every table and recreating it empty, when its schema version is newer than the build's, and SHALL log a warning when it does. It SHALL NOT reset for a missing upgrade path: that fails loudly like in release builds.

#### Scenario: Debug build opens a newer database
- **WHEN** a debug build opens a database whose schema version is higher than the build's
- **THEN** the database is recreated empty with the current schema and a warning is logged

#### Scenario: Debug build with a forgotten migration
- **WHEN** a debug build opens a database from an older schema version and no migration path exists to the current one
- **THEN** opening fails with an error and the data is unchanged
