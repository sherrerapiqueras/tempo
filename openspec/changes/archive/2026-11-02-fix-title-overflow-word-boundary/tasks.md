# Tasks

## 1. Shared split

- [x] 1.1 Add the title split and description join helper in `core/ui/editor/`, with unit tests covering word boundaries, pastes, long words, emoji and the cursor; verify with `./gradlew testDebugUnitTest --tests "*EditorTitleSplitTest"`

## 2. Editors

- [x] 2.1 Use the helper in the task and habit title handlers and add a mid-word overflow test to each sheet's instrumented tests; verify with `./gradlew :app:compileDebugAndroidTestKotlin` and by running the sheet tests on an AVD

## 3. Verification

- [x] 3.1 Run `openspec validate fix-title-overflow-word-boundary --strict`, `./gradlew ktlintCheck`, `./gradlew :app:detekt`, `./gradlew testDebugUnitTest` and `./gradlew koverVerifyDebug`, and verify all pass

## Workflow follow-up

- Archive the change in the same PR before its final CI run.
