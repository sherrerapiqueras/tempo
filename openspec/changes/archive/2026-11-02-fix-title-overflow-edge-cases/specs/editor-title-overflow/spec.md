# Spec Delta

## MODIFIED Requirements

### Requirement: Hard split keeps characters whole
When the text has no word boundary that leaves a usefully long title, the system SHALL split at the last user-perceived character boundary at or before the limit, and SHALL NOT cut a surrogate pair.

#### Scenario: A single long word
- **WHEN** the text is one word longer than the limit
- **THEN** the title holds the characters that fit and the rest moves to the description

#### Scenario: An emoji crosses the limit
- **WHEN** an emoji, including a joined sequence, flag or variation sequence, crosses the limit in text with no word boundary
- **THEN** the whole emoji moves to the description

#### Scenario: Leading whitespace only
- **WHEN** the only whitespace before the limit is at the start of the text
- **THEN** the system hard splits the text rather than leaving the title blank

#### Scenario: A word boundary would leave a very short title
- **WHEN** the last word boundary leaves fewer than 16 characters in the title
- **THEN** the system hard splits the text rather than moving nearly the whole title

#### Scenario: A non-breaking space crosses the limit
- **WHEN** the only whitespace before the limit is a non-breaking space
- **THEN** the system does not split at it

#### Scenario: One character longer than the limit
- **WHEN** a single user-perceived character is itself longer than the limit
- **THEN** the system splits inside it at a code point boundary rather than leaving the title empty

### Requirement: Moved text joins the description with the cursor after it
The system SHALL place moved text at the start of the description, keep it apart from existing description text, focus the description, and put the cursor directly after the moved text.

#### Scenario: Description already has text
- **WHEN** overflow moves into a description that already has text
- **THEN** the description is the moved text, a space, then the existing text, with the cursor after the moved text

#### Scenario: Description starts with a list
- **WHEN** overflow moves into a description whose first line is a dash list item
- **THEN** the list item stays on its own line below the moved text

#### Scenario: Description already starts with whitespace
- **WHEN** overflow moves into a description that starts with whitespace
- **THEN** the system adds no further separator

#### Scenario: Description is empty
- **WHEN** overflow moves into an empty description
- **THEN** the description is the moved text with the cursor at its end

#### Scenario: Only whitespace overflowed
- **WHEN** the user types a space with the title already at the limit
- **THEN** the title is unchanged, the description takes focus with the cursor at its start, and the next word typed stays apart from existing description text

#### Scenario: The same keystroke overflows twice
- **WHEN** the user returns to a title at the limit and repeats a keystroke that overflowed before
- **THEN** the edit moves to the description again

## ADDED Requirements

### Requirement: Pasted line breaks are kept
When title text contains a line break that the Enter key did not add, the system SHALL keep the first non-blank line in the title and move the remaining lines to the description, preserving their line breaks.

#### Scenario: Pasting several lines
- **WHEN** a user pastes text containing line breaks into the title
- **THEN** the title holds the first line and the description starts with the following lines

#### Scenario: Pressing Enter
- **WHEN** a user presses Enter in the title
- **THEN** the title is unchanged and the title field loses focus

### Requirement: Title errors follow the title
Any title edit, including one that moves text to the description, SHALL clear title errors when the resulting title has text and SHALL show the blank-title error when it does not.

#### Scenario: Pasting over a blank-title error
- **WHEN** a blank-title error is showing and a user pastes text past the limit
- **THEN** the error clears

#### Scenario: Pasting only whitespace past the limit
- **WHEN** a user pastes only whitespace past the limit
- **THEN** the title is empty, the blank-title error shows, and focus stays on the title
