# Proposal

## Why

The task and habit editors move title text past 65 characters into the description and jump focus there. The keyboard has to reconnect during that jump, and characters typed in the gap reach neither field. On an emulator, with the real on-screen keyboard, about 100–400ms between taps lost one to four of the five characters after the jump; the same taps with no jump lost one of 54. The last release loses them too.

The move needs a lot of code for an uncommon case, and titles past the limit already have a safe path: validation rejects them on save with an error.

## What Changes

- **BREAKING (behaviour):** typing or pasting past 65 characters no longer moves text to the description or moves focus. The text stays in the title.
- The editor no longer has its own 65-character limit. The existing 500-character validation stays, and a longer title shows the existing error on save with nothing discarded.
- Line breaks in pasted titles become single spaces, instead of later lines moving to the description.
- Enter and the keyboard's Done action still clear focus from the title.

Non-goals:

- Lowering or otherwise changing the 500-character validation limit.
- Changing how cards, Focus or notifications show titles, which already cap their lines.
- Pinning the sheet footer above the keyboard, tracked in #523.

## Capabilities

### New Capabilities

- `editor-title-input`: how the task and habit editors accept long titles, pasted line breaks and the Enter key.

### Modified Capabilities

- `editor-title-overflow`: removed, because its behaviour no longer exists.

## Impact

Task and habit editor sheets, in every placement. The layout is unchanged at every window size. Removes `EditorTitleSplit` and `EditorTitleFieldState`, with their tests.
