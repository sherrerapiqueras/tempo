# Tasks

## 1. Database: default category and schema-mismatch policy

- [x] 1.1 In `TempoDatabase`, seed the default category in `onOpen` with the conditional `INSERT OR IGNORE` from the design, keep `DbKdfIterMarker.write(...)` as the only thing `onCreate` does, and log a warning in `onDestructiveMigration`. Verify: `./gradlew :app:compileDebugKotlin` passes.
- [x] 1.2 Move migrations, both callbacks and the downgrade policy into one `internal` builder configuration taking `allowDestructiveDowngrade`; `getDatabase` passes `BuildConfig.DEBUG` and no longer calls `fallbackToDestructiveMigration`. Verify: `./gradlew :app:compileDebugKotlin` passes and the schema check (`./gradlew kspDebugKotlin`) leaves `app/schemas/` unchanged.
- [x] 1.3 Instrumented tests (new `DefaultCategorySeedingTest`, existing `DefaultCategoryCallbackTest` kept): fresh create seeds the Inbox; reopen of a database without categories restores it; a renamed/recoloured Inbox is untouched; a deleted non-default Inbox is not resurrected while another default exists; a downgrade with `allowDestructiveDowngrade = true` leaves an empty database with the Inbox; with `false` it throws and the file is unchanged. Verify: `adb shell am instrument` on a fresh emulator reports all of them passing.

## 2. Tasks screen: no category, no task

- [x] 2.1 Add `NO_CATEGORY_ID`, use it in `TaskBottomSheetContent`, and make `loadData` keep the current selection (and store nothing) when there are no categories. Verify: unit test in `TasksViewModelTest` (selection kept, `setSelectedCategoryId` not called).
- [x] 2.2 In `TasksViewModelTaskActions`, refuse to open the new-task editor when categories have loaded and are empty, and refuse `createOrUpdateTask` for `NO_CATEGORY_ID` without closing the editor, both with a snackbar. Add the string to `values` and `values-es`. Verify: unit tests for both guards pass, and `./gradlew lintDebug` reports no `MissingTranslation`.

## 3. Verification

- [x] 3.1 Run `./gradlew ktlintFormat ktlintCheck :app:detekt testDebugUnitTest koverVerifyDebug lintDebug validateDebugScreenshotTest` and check the detekt baseline stays at 155 or lower. (Local `validateDebugScreenshotTest` shows the same six `FocusContentMidDay`/`DockedFocusScreenEditTask` mismatches on a clean `main` checkout, macOS renders; CI's Linux render is the authority.)
- [x] 3.2 Reproduce the original symptom on a fresh AVD (not the user's data-holding ones): install a build, open the database at a newer `user_version`, relaunch a debug build, and see the Inbox and the category row back. Verify against the debug build on the emulator. (Done on a throwaway AVD: a schema-15 build, then this build at 14; the log shows the reset warning and both the Tasks screen and the add-task sheet list the Inbox.)
- [x] 3.3 `openspec validate fix-inbox-missing-after-db-reset --strict` passes.

## Workflow follow-up

- Archive the change in the same PR, before the final verification run, once 1.x to 3.x are done.
