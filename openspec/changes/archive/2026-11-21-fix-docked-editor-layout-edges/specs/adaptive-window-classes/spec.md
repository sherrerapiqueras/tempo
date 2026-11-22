# Spec Delta

## ADDED Requirements

### Requirement: Docked editor sits beside content with a clean seam
When an editor docks beside screen content, the content block and the editor pane SHALL be separated by a gutter equal to the gap between the floating rail and the content, at every Large and Extra-large width. The pane's surface SHALL start at the same height as the content block and end at the window's bottom edge like it. The expanded rail's content, including the app title, SHALL stay within the rail, so it never overlaps screen content.

#### Scenario: Editor docked at 1280 dp
- **WHEN** the window is 1280 dp wide and a task is open in the docked editor
- **THEN** the category chips stay clear of the rail title, a gutter separates the list block from the editor pane, and both surfaces start at the same height

#### Scenario: Editor docked at 1600 dp
- **WHEN** the window is 1600 dp wide and a task is open in the docked editor
- **THEN** the gutter between the list block and the editor pane is the same as at 1280 dp

## MODIFIED Requirements

### Requirement: Layout changes glide
When the rail clearance or horizontal position of screen content changes, because the window crosses a breakpoint or an editor docks beside the list, the content SHALL move to its new position with an animation, not in a single frame.

#### Scenario: Window resized across Medium
- **WHEN** a desktop window is widened from 500 dp to 700 dp
- **THEN** the rail appears and the content slides to clear it

#### Scenario: Editor docks
- **WHEN** a task is opened on a Large window
- **THEN** the editor pane and its gutter grow in from the end edge and the list slides aside with them
