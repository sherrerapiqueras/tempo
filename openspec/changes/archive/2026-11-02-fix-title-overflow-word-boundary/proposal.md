# Proposal

## Why

When a task or habit title passes the 65-character editor limit, the overflow moves to the description at exactly character 65. A word can end up split across the two fields ("cua" stays in the title and "ndo" goes to the description), and a hard cut can break an emoji in half. There is no originating GitHub issue; this came up while reviewing the task sheet on an emulator.

## What Changes

- The title editor moves overflow at the last word boundary at or before the limit, so the partial word travels to the description whole.
- Whitespace at the split is dropped rather than left at the end of the title or the start of the description.
- A title with no usable word boundary falls back to a hard split that never cuts a surrogate pair or an emoji sequence.
- Moved text is separated from existing description text by a space, so the carried word is not glued to the description's first word.
- The task and habit editors share one split, so they cannot drift apart.

**Non-goals**

- Changing the 65-character limit, or the 500-character domain rule in `ValidationUtils`.
- Keeping the cursor in the title when text is inserted mid-title. The title callback only receives a string, so overflow still moves focus to the description.
- Changing how a newline in the title is handled.

## Capabilities

### New Capabilities

- `editor-title-overflow`: how the task and habit editors move text past the title limit into the description.

### Modified Capabilities

None.

## Impact

- `core/ui/editor/`: new pure split helper with unit tests.
- `TaskBottomSheetContent.kt`, `HabitBottomSheetContent.kt`: title change handlers.
- `TaskBottomSheetTest.kt`, `HabitBottomSheetTest.kt`: overflow coverage.
- No data, navigation or string resource changes.
