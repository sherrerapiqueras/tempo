# Design

## Context

See proposal.md for why. `splitEditorTitle` and `descriptionWithTitleOverflow` decide the move, a `LaunchedEffect` moves focus, and `EditorTitleFieldState` exists only so a repeated overflowing keystroke isn't ignored by the `String` field.

## Goals / Non-Goals

**Goals:** no focus jump while typing, and less code.

**Non-Goals:** see proposal.md.

## Decisions

**Remove the move rather than defer it.** Moving the overflow when the title loses focus would also stop the loss, but it keeps all of the split rules and adds a split at every save path. Removing it needs one small function.

**Keep 500 as a safety limit, not a typing limit.** Blocking keys at a limit would silently ignore input, which is the kind of loss this change removes. Existing validation already reports a title that's too long and keeps the text. A lower limit would be reached more often for little benefit, because every display caps its lines.

**Go back to the `String` text field.** `EditorTitleFieldState` only worked around repeated overflow. The sheets already own the title as a `String`.

**Handle Enter as a key press.** Intercepting the key before the field sees it means a repeated Enter still fires and a selection isn't replaced, which is simpler than keeping `EditorTitleFieldState` for it. A newline committed by a keyboard app still goes through the Enter check in `resolveEditorTitleEdit`.

**Warn on closing a title that autosave can't store.** Autosaving editors skip the discard prompt because everything is already saved, which isn't true past the validation limit.

**Flatten pasted line breaks to spaces.** The title has no second line to keep, and moving lines to the description is the behaviour being removed. Nothing is discarded.

## Risks / Trade-offs

- Long titles are now reachable in practice, where the editor capped them at 65. Cards and Focus already cap their lines, and the chain selector's chips sit in a row that scrolls sideways. Mitigation: check a long title on the emulator.
- A long title shows three lines in the editor and scrolls while focused, with no sign of more text when it isn't focused. Accepted: it's uncommon and nothing is lost.
- Autosave of a title over 500 characters reports the same error as a manual save. Accepted.
