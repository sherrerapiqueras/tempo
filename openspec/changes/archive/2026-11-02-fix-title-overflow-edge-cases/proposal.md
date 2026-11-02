# Proposal

## Why

A review of the word-boundary title overflow in `fix-title-overflow-word-boundary` found edge cases it left wrong: a multi-line paste is discarded, title errors go stale when text moves, and the next word can still glue to the description. There is no originating GitHub issue.

## What Changes

- Pasted line breaks no longer discard the paste: the first line stays in the title and the rest moves to the description.
- Title errors follow the resulting title on every edit, including one that moves text.
- Moved text stays apart from existing description text in every case, and a dash list keeps its own line.
- A word boundary that would leave a very short title is skipped in favour of a hard split, and non-breaking spaces are not boundaries.
- Repeating a keystroke that overflowed before moves it again instead of being lost.
- Both editors resolve title edits through one shared function.

**Non-goals**

- Changing the 65-character limit.
- Keeping the cursor in the title when text is inserted mid-title.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `editor-title-overflow`: covers line breaks, title errors, description separators, short titles, non-breaking spaces and repeated keystrokes.

## Impact

- `core/ui/editor/`: expanded split helper, new title field state, unit and device tests.
- Task and habit bottom sheet content and form sections, with instrumented tests.
- No data, navigation or string resource changes.
