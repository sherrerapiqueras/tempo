# Spec Delta

## Purpose

Defines how the task and habit editors move text past the title limit into the description, so no word or emoji is split between the two fields.

## ADDED Requirements

### Requirement: Overflow moves at a word boundary
When title text passes the editor's title limit, the system SHALL keep the words that fit in the title and move the rest to the description, splitting at the last whitespace at or before the limit and dropping the whitespace at the split.

#### Scenario: Typing past the limit mid-word
- **WHEN** a user types a character that takes the title past the limit while in the middle of a word
- **THEN** the title ends at the previous word and the whole partial word moves to the description

#### Scenario: Pasting text longer than the limit
- **WHEN** a user pastes text much longer than the limit that contains spaces
- **THEN** the title holds the words that fit and all remaining text moves to the description in its original order

#### Scenario: A space falls right after the limit
- **WHEN** the last word that fits ends exactly at the limit and is followed by whitespace
- **THEN** that word stays in the title and the whitespace is in neither field

#### Scenario: Text at the limit
- **WHEN** the title text is exactly at the limit
- **THEN** the title keeps all of the text and the description is unchanged

### Requirement: Hard split keeps characters whole
When the text has no word boundary that leaves visible text in the title, the system SHALL split at the last user-perceived character boundary at or before the limit, and SHALL NOT cut a surrogate pair.

#### Scenario: A single long word
- **WHEN** the text is one word longer than the limit
- **THEN** the title holds the characters that fit and the rest moves to the description

#### Scenario: An emoji crosses the limit
- **WHEN** an emoji, including a joined sequence, flag or variation sequence, crosses the limit in text with no word boundary
- **THEN** the whole emoji moves to the description

#### Scenario: Leading whitespace only
- **WHEN** the only whitespace before the limit is at the start of the text
- **THEN** the system hard splits the text rather than leaving the title blank

#### Scenario: One character longer than the limit
- **WHEN** a single user-perceived character is itself longer than the limit
- **THEN** the system splits inside it at a code point boundary rather than leaving the title empty

### Requirement: Moved text joins the description with the cursor after it
The system SHALL place moved text at the start of the description, separate it from existing description text with a space, focus the description, and put the cursor directly after the moved text.

#### Scenario: Description already has text
- **WHEN** overflow moves into a description that already has text
- **THEN** the description is the moved text, a space, then the existing text, with the cursor after the moved text

#### Scenario: Description is empty
- **WHEN** overflow moves into an empty description
- **THEN** the description is the moved text with the cursor at its end

#### Scenario: Only whitespace overflowed
- **WHEN** the user types a space with the title already at the limit
- **THEN** the title and the description text are unchanged, the description takes focus, and the cursor is at its start
