## 1. Space entity and migration

- [ ] 1.1 Add `SpaceEntity` (name, icon, colorKey, sortOrder, isDefault, enabledTabs, defaultTab) and `SpaceDao`
- [ ] 1.2 Add nullable `spaceId` to `CategoryEntity`, `HabitEntity` and `HabitChainEntity` with indices
- [ ] 1.3 Write the Room migration: seed one default space adopting the current global tab configuration, assign every existing category, habit and chain to it, then make `spaceId` non-null
- [ ] 1.4 Regenerate and commit the exported Room schema (`./gradlew kspDebugKotlin`)
- [ ] 1.5 Add domain `Space` model, repository interface and mappers; bind the repository in `core/di/RepositoryModule.kt`
- [ ] 1.6 Migration tests, including a real pre-migration database rather than only a synthetic one

## 2. Per-space default categories

The reserved Inbox id was removed in milestone 1.1 by #326: the default is the row with `isDefault`, and the seeded Inbox is the row with the well-known `uid`. What remains is tracked by #545.

- [ ] 2.1 Scope `isDefault` per space, so setting a default clears only that space's previous default
- [ ] 2.2 Resolve default categories per space in `BackupRepositoryImpl`
- [ ] 2.3 Seed a default category whenever a space is created, and prevent deleting a space's default category
- [ ] 2.4 Extend the `default-category-persistence` regression tests for the per-space case

## 3. Active space and scoping

- [ ] 3.1 Store the active space in `SharedPreferences` via a repository bound in `core/di/PreferencesRepositoryModule.kt`; exclude it from backup
- [ ] 3.2 Apply space filtering at the category, task, habit and chain repository boundaries
- [ ] 3.3 Assign the active space when creating a category, habit or chain
- [ ] 3.4 Enforce the chain/habit same-space invariant at the repository boundary and filter habit selection in the chain editor
- [ ] 3.5 Tests asserting that no other-space content is returned by any listing

## 4. Switcher UI

- [ ] 4.1 Space switcher in the `TempoTopBar` actions slot at compact widths, opening a bottom sheet
- [ ] 4.2 Switcher chip anchored above the navigation rail at medium and expanded widths, with an anchored menu
- [ ] 4.3 Show the space name on the labeled rail at expanded widths
- [ ] 4.4 Hide every space affordance while only one space exists
- [ ] 4.5 Keyboard switching by space position, alongside the existing pointer/keyboard affordances
- [ ] 4.6 Dismiss an open editor pane or sheet on space change, through the existing unsaved-changes guard
- [ ] 4.7 UI tests for the Content composables, including the single-space case and constrained widths

## 5. Space management

- [ ] 5.1 Space management surface in Settings: create, rename, reorder, delete
- [ ] 5.2 Prompt for the default space's name when a second space is created
- [ ] 5.3 Deletion flow offering both move-content and delete-content, with the destructive option never pre-selected; skip the choice for an empty space; prevent deleting the last space; activate another space when the active one is deleted
- [ ] 5.4 Move enabled-tabs and default-tab configuration to per-space, leaving legacy preference keys in place for rollback
- [ ] 5.5 Localise every new string into all existing locales and verify with `./gradlew lintDebug`

## 6. Focus

- [ ] 6.1 Order the Focus agenda with the active space first, then a "From other spaces" divider, then the rest ordered among themselves
- [ ] 6.2 Scope Up next to the active space
- [ ] 6.3 Show a space indicator below the divider only, never above it
- [ ] 6.4 Initial state of the other-spaces block from the density threshold derived per window size class (collapsed with a count when the active space is full, expanded when sparse), overridden to expanded when an item below the divider has a reminder within two hours; manual toggling persists while Focus is open and re-evaluates on re-entry
- [ ] 6.5 Allow a running session to continue across a space change, and identify its space on the session surface and notification when it differs from the active one
- [ ] 6.6 Keep focus activity and streak spanning all spaces
- [ ] 6.7 Tests for divider suppression with one space and with no other-space items

## 7. Entry points outside the app

- [ ] 7.1 Ask which space a quick-task widget targets when it is placed, and keep it fixed regardless of the active space
- [ ] 7.2 Switch to the item's space when a reminder notification is opened
- [ ] 7.3 Verify reminders fire for items in non-active spaces

## 8. Backup

- [ ] 8.1 Add a spaces section and `spaceId` fields to the backup DTOs and bump `schemaVersion`
- [ ] 8.2 Import older backups into the default space
- [ ] 8.3 Update `docs/BACKUP_FORMAT.md`
- [ ] 8.4 Regression tests for old-version and new-version imports

## 9. Verification

- [ ] 9.1 `./gradlew ktlintFormat` and `./gradlew ktlintCheck`
- [ ] 9.2 `./gradlew :app:detekt` with the baseline not grown
- [ ] 9.3 `./gradlew testDebugUnitTest` and `./gradlew koverVerifyDebug`
- [ ] 9.4 `./gradlew lintDebug` for translation completeness
- [ ] 9.5 `connectedDebugAndroidTest` on the Pixel 10 AVD
- [ ] 9.6 Manual pass: single-space install unchanged; two-space switching and Focus block density at compact, medium and expanded widths
- [ ] 9.7 `openspec validate feat-324-spaces`
- [ ] 9.8 Add a `WhatsNewRegistry.latest` entry for the feature and remove the previous entry's unused strings
