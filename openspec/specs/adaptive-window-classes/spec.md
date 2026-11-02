# adaptive-window-classes Specification

## Purpose
Defines how window width maps to the app's layout tiers and how the layout moves between them.

## Requirements

### Requirement: Official width classes decide every layout tier
The app SHALL classify window width with the Material 3 / WindowManager width classes (Compact < 600 dp ≤ Medium < 840 dp ≤ Expanded < 1200 dp ≤ Large < 1600 dp ≤ Extra-large). The floating rail SHALL replace the bottom bar from Medium up, and editors SHALL dock beside content from Large up, both derived from the same classification.

#### Scenario: Large desktop window
- **WHEN** the window is 1280 dp wide
- **THEN** navigation is a floating rail and editors dock beside the content

#### Scenario: Extra-large display
- **WHEN** the window is 1920 dp wide
- **THEN** it behaves as Large

#### Scenario: Boundary
- **WHEN** the window is 1199 dp wide
- **THEN** editors open as bottom sheets, and the rail is shown

### Requirement: Layout changes glide
When the rail clearance of screen content changes, because the window crosses a breakpoint or an editor docks beside the list, the content SHALL move to its new position with an animation, not in a single frame.

#### Scenario: Window resized across Medium
- **WHEN** a desktop window is widened from 500 dp to 700 dp
- **THEN** the rail appears and the content slides to clear it

### Requirement: Docking keeps the screen in view
Opening or closing a docked editor SHALL NOT be presented as leaving the screen beside it. Throughout the pane's appearance and disappearance, the screen's content (its header, chips and list) SHALL stay drawn at full opacity, and the pane SHALL visibly grow in from, or shrink toward, the end edge. Navigation between different screens SHALL keep its own transitions.

#### Scenario: Open a task on a Large window
- **WHEN** the window is 1280 dp wide and the user opens a task in Tasks
- **THEN** no frame shows the list or category chips missing or faded, and the editor pane grows in from the end edge

#### Scenario: Close the docked editor
- **WHEN** the user closes the docked editor with Back or by tapping the open task again
- **THEN** no frame shows the list missing or faded, and the pane shrinks toward the end edge

#### Scenario: Open a habit on a Large window
- **WHEN** the window is 1280 dp wide and the user opens a habit in Routines
- **THEN** no frame shows the routines list missing or faded, and the editor pane grows in from the end edge

#### Scenario: Switch tabs with an editor docked
- **WHEN** an editor is docked in Tasks and the user switches to Routines
- **THEN** the tab switch behaves as it does without an editor docked

### Requirement: Focus docks its task editor like the other tabs
From Large up, a task editor opened on the Focus tab from the agenda, from a subtask action, or from "Add Task" SHALL dock beside the agenda, as the Tasks editor docks beside the task list. The agenda SHALL stay visible and usable beside it, and the task being edited SHALL be shown as selected. A task opened from the Plan tasks sheet SHALL instead open as a sheet over that sheet. Below Large, the Focus task editor SHALL remain a bottom sheet.

#### Scenario: Open a task from the agenda on a tablet
- **WHEN** the window is 1280 dp wide and the user taps "Responder correos de clientes" in the Focus agenda
- **THEN** the task editor docks beside the agenda, the agenda stays visible without a scrim, and that task's card is shown as selected

#### Scenario: Open another task while one is docked
- **WHEN** a task is docked on Focus and the user taps a different task in the agenda
- **THEN** the pane shows the newly tapped task, and its card becomes the selected one

#### Scenario: Add a task from Focus on a Large window
- **WHEN** the window is 1280 dp wide and the user taps "Add Task" on Focus
- **THEN** a blank task editor docks beside the agenda

#### Scenario: Open a task while planning
- **WHEN** the Plan tasks sheet is open and the user opens one of its tasks
- **THEN** the task editor opens as a sheet over the Plan tasks sheet, and closing it returns to planning

#### Scenario: Phone
- **WHEN** the window is Compact and the user opens a task from the Focus agenda
- **THEN** the task editor opens as a bottom sheet, as before

### Requirement: Focus docks the running session
From Large up, the focus session SHALL dock beside the Focus agenda, in the task editor's pane, and the session pill on any tab SHALL switch to Focus with it docked. The pane SHALL hold whichever of a task and the session was opened last; a docked task closes through its unsaved-changes guard. Below Large, the session SHALL remain a bottom sheet.

#### Scenario: Open the session from another tab
- **WHEN** the window is 1280 dp wide, a session is running and the user taps the session pill on the Tasks tab
- **THEN** Focus is shown with the session docked beside the agenda

#### Scenario: A task replaces the session
- **WHEN** the session is docked and the user taps a task in the Focus agenda
- **THEN** the task editor takes the session's place in the pane, and the session pill brings the session back

#### Scenario: The session replaces a task
- **WHEN** a task is docked on Focus and the user taps the session pill
- **THEN** the task closes, confirming unsaved changes first, and the session is docked in its place

#### Scenario: Close the docked session
- **WHEN** the session is docked and the user presses Back
- **THEN** the pane closes and Focus stays open

#### Scenario: Smaller window
- **WHEN** the window is 1050 dp wide and the user opens the session
- **THEN** it opens as a bottom sheet, as before
