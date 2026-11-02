# Design

## Context

See proposal.md - Why. The title field reports a whole string, and the sheets own the title and description state.

## Goals / Non-Goals

**Goals:**

- Keep every title edit rule in one pure, unit-testable function, so the sheets only apply the result.

**Non-Goals:**

- Moving title state out of the sheets.

## Decisions

- **Non-breaking spaces are not boundaries.** They exist to prevent a break, so they are excluded from the whitespace check.
- **A boundary must leave at least 16 characters.** This also covers leading spaces before a long word. A shorter title is usually one small word in front of a very long one, so a hard split keeps more of the title. Reaching this needs a word of about 50 characters, which in practice means a URL or path.
- **Enter is recognised by the edit.** A newline counts as Enter only if removing it gives back the current title; anything else is a paste. An Enter that also replaces a selection therefore counts as a paste, which is harmless because the lines still end up in the right fields.
- **A leading space protects the next word.** When only whitespace overflowed, a space is added before existing description text, because the next word typed lands right in front of it. Saving trims the description, so it is never stored.
- **The title field keeps its own `TextFieldValue`.** The `String` overload of `TextField` ignores an edit equal to the last text it reported, which loses a repeated keystroke when the sheet left the title unchanged. A small state holder avoids that without moving title state out of the sheets.

## Risks / Trade-offs

- [A hard split can cut a long URL after a very short first word] → Accepted: the user can edit either field, and the rest of the URL is kept in the description.
- [Device emoji coverage only runs on the AVD API level used] → The fallback still never cuts a surrogate pair on older ICU versions.
- [A space typed at a full title marks an existing item as edited through its description's leading space] → Accepted: the user did edit, and saving trims the space.
