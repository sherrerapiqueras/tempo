# Tasks

## 1. One list column

- [x] 1.1 Remove the sectioned width from `TasksScreen` and `NavigationComponents`, and verify the app builds with `assembleDebug`
- [x] 1.2 Remove the grid branch and `isTwoColumn` from `TasksContent` and `TasksSectionShortcuts`, delete `TasksContentSectionGrid.kt`, and verify no reference to the grid remains
- [x] 1.3 Remove the grid keys from `SectionListLayout` and their test, and verify `testDebugUnitTest` passes

## 2. Screenshots

- [x] 2.1 Update the `TasksSectionedScreenshots` KDoc, regenerate its references, review the diff report, and verify `validateDebugScreenshotTest` passes

## 3. Validation

- [x] 3.1 Validate on Pixel Tablet landscape (1280 dp) and portrait (800 dp), including creating the first section live, and verify the list keeps its column and the chips don't move sideways
- [x] 3.2 Validate on Large Desktop maximised and resized, and on a phone, and verify the same column behaviour, with the shortcut group shown only at Large and up
- [x] 3.3 Validate a docked editor opening beside a sectioned list, and verify the list stays one column
- [x] 3.4 Run `openspec validate fix-510-sections-list-keeps-layout --strict` and verify it passes
- [x] 3.5 Run `ktlintFormat`, `ktlintCheck`, `:app:detekt`, `lintDebug`, `testDebugUnitTest` and `koverVerifyDebug`, and verify they all pass

## Workflow follow-up

- Archive the change in the same PR, before its final CI run.
- Update the main spec's Purpose, which still mentions the two-column flow.
