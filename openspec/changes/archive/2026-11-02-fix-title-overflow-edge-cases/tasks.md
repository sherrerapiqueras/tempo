# Tasks

## 1. Title edit rules

- [x] 1.1 Rework the split and description join and add the shared title edit function, with unit tests for line breaks, separators, non-breaking spaces and short titles; verify with `./gradlew testDebugUnitTest --tests "*EditorTitleSplitTest"`
- [x] 1.2 Add the title field state, with unit tests for repeated edits; verify with `./gradlew testDebugUnitTest --tests "*EditorTitleSplitTest"`

## 2. Editors

- [x] 2.1 Use the shared function and field state in the task and habit sheets, with instrumented tests for pasted lines and repeated keystrokes and a device test for emoji; verify by running `EditorTitleSplitDeviceTest`, `TaskBottomSheetTest` and `HabitBottomSheetTest` on an AVD

## 3. Verification

- [x] 3.1 Run `openspec validate fix-title-overflow-edge-cases --strict`, `./gradlew ktlintCheck`, `./gradlew :app:detekt`, `./gradlew testDebugUnitTest` and `./gradlew koverVerifyDebug`, and verify all pass

## Workflow follow-up

- Archive the change in the same PR before its final CI run.
