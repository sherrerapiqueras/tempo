# editor-title-input Specification

## Purpose
Defines how the task and habit editors accept long titles, pasted line breaks and the Enter key, so typed text is never lost or moved between fields.

## Requirements

### Requirement: Long titles stay in the title

The system SHALL keep all typed and pasted title text in the title field, and SHALL NOT move text to the description or move focus because of the title's length.

#### Scenario: Typing past 65 characters

- **WHEN** a user keeps typing a title past 65 characters
- **THEN** every character stays in the title, focus stays on the title and the description is unchanged

#### Scenario: Pasting long text

- **WHEN** a user pastes text longer than the title field can show
- **THEN** all of the text is in the title and the field scrolls to show it

### Requirement: Long titles show up to five lines

The system SHALL grow the title field with its text up to five visible lines, and SHALL scroll within the field past that.

#### Scenario: A title longer than five lines

- **WHEN** a title needs more than five lines
- **THEN** the field shows five lines and scrolls to reach the rest

### Requirement: Titles over the validation limit are rejected on save

When a title is longer than the 500-character validation limit, the system SHALL show the title-too-long error on save and SHALL keep the text in the field.

#### Scenario: Saving a title over the limit

- **WHEN** a user saves a title longer than 500 characters
- **THEN** the item is not saved, the title-too-long error shows and the title text is unchanged

#### Scenario: Closing an autosaving editor over the limit

- **WHEN** a user closes an editor for an existing item while its title is longer than 500 characters
- **THEN** the discard confirmation shows before the edit is lost

### Requirement: Pasted line breaks become spaces

When title text contains a line break that the Enter key did not add, the system SHALL replace each line break and the whitespace around it with a single space, and SHALL trim the ends of the result.

#### Scenario: Pasting several lines

- **WHEN** a user pastes text containing line breaks into the title
- **THEN** the title holds all of the lines on one line, separated by single spaces

#### Scenario: Pressing Enter

- **WHEN** a user presses Enter in the title
- **THEN** the title is unchanged and the title field loses focus

#### Scenario: Pressing Enter again later

- **WHEN** a user returns to the title and presses Enter again
- **THEN** the title field loses focus again

#### Scenario: Pressing Enter with text selected

- **WHEN** a user presses Enter with part of the title selected
- **THEN** the title is unchanged and the title field loses focus

### Requirement: Title errors follow the title

Any title edit SHALL clear title errors when the resulting title has text and SHALL show the blank-title error when it does not.

#### Scenario: Pasting over a blank-title error

- **WHEN** a blank-title error is showing and a user pastes text
- **THEN** the error clears

#### Scenario: Pasting only line breaks and whitespace

- **WHEN** a user pastes only line breaks and whitespace
- **THEN** the title is empty and the blank-title error shows
