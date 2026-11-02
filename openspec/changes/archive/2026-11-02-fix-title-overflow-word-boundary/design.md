# Design

## Context

Both editors duplicate the same overflow branch in their title change handler. See proposal.md - Why. The title field reports a plain string, so the handler knows the new text but not the cursor position.

## Goals / Non-Goals

**Goals:**

- One pure, unit-testable split shared by the task and habit editors.

**Non-Goals:**

- Dictionary-based word breaking. Scripts written without spaces use the hard split.

## Decisions

- **Whitespace is the word boundary.** `Char.isWhitespace` covers the separators people actually type, including the non-breaking space. A locale-aware word iterator was rejected: it would split at hyphens and apostrophes, moving "well-" apart from "known" for no benefit.
- **`java.text.BreakIterator` finds the hard split.** A surrogate check alone would still break joined emoji, flags and variation sequences. The class is available at minSdk 24 and needs no new dependency, and on Android and JDK 20+ it follows extended grapheme clusters.
- **A code point cut backs up the grapheme search.** A single cluster longer than the limit has no earlier boundary, so the split falls back to the limit, moved back one if that would cut a surrogate pair. The title never comes back empty because of it.
- **Blank prefixes do not count as boundaries.** Otherwise leading spaces before a long word would empty the title.
- **Focus still moves when only whitespace overflowed.** Keeping focus in a full title would make the next typed character pull the title's last word into the description.
- **The helper builds the description value too.** The separator and cursor rule are part of the same behaviour, and sharing them keeps both editors identical.

## Risks / Trade-offs

- [An older Android ICU may not recognise an emoji sequence newer than itself] → It still never cuts a surrogate pair, so the worst case is a sequence split between valid code points.
- [A long word early in the text leaves a short title] → Accepted: the split rule is the last boundary, and the user can edit either field.
