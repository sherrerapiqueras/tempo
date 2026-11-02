# Spec Delta

## ADDED Requirements

### Requirement: Sections don't change the list's width or position
The Tasks list SHALL have the same maximum width, horizontal position and side margins whether or not the selected category has sections, at every window width. Unsectioned tasks and sectioned tasks SHALL be cards of the same width in one column.

#### Scenario: First section on a wide window
- **WHEN** on a 1280 dp window the user creates the first section in a category
- **THEN** the list stays in its centred 840 dp column with the same side margins, and its cards keep their width

#### Scenario: Unsectioned and sectioned tasks together
- **WHEN** a 1280 dp window shows a category with one unsectioned task and two sections
- **THEN** all its tasks are in one column, in order, as cards of the same width

#### Scenario: Switching categories
- **WHEN** on a 1280 dp window the user switches from a category without sections to one with sections
- **THEN** the category chips and the list keep their horizontal position

#### Scenario: Editor docks beside a sectioned list
- **WHEN** the user opens a task and the editor docks beside a sectioned list
- **THEN** the list stays one column, without changing layout

## MODIFIED Requirements

### Requirement: Large and up shows a section shortcut group
At Large width and up, a category with sections SHALL show a connected single-choice button group under the category row, within the width of the list column. It SHALL hold one segment per section plus "No section" when unsectioned tasks exist, each with its count, followed by separate Add section and Reorder sections buttons. Segments that don't fit SHALL scroll sideways, with the two buttons staying in view.

#### Scenario: Jump to section
- **WHEN** on a tablet in landscape the user taps the "Shopping" segment
- **THEN** the list scrolls so that the "Shopping" header is at the top, and the "Shopping" segment is checked

#### Scenario: Not shown below Large
- **WHEN** the window is Expanded width or narrower
- **THEN** no section shortcut group is shown

#### Scenario: More sections than fit
- **WHEN** on a 1280 dp window a category has more sections than fit in the list column
- **THEN** the segments scroll sideways inside the column, and Add section and Reorder sections stay visible

## REMOVED Requirements

### Requirement: Sections flow into two columns when the list is wide
**Reason**: Two columns made a list rearrange itself when its first section appeared, and made unsectioned and sectioned tasks different widths (#510).
**Migration**: None needed. Sections stay as they are and are listed in one column; #468 tracks any future multi-column layout.
