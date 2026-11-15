# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Focus offers the add-task action`
- TO: `### Requirement: Focus offers the add-session action`

## MODIFIED Requirements

### Requirement: Focus offers the add-session action
While the Focus tab is active, the floating navigation SHALL show an "Add session" action in the place the Tasks tab shows "Add Task", in every layout: portrait bar, navigation rail, and single-tab mode. It SHALL NOT show the Tasks sort action.

#### Scenario: Portrait bar on Focus
- **WHEN** the user is on the Focus tab in a portrait layout
- **THEN** the floating bar shows the "Add session" button beside the navigation pill and no sort button

#### Scenario: Focus as the only tab
- **WHEN** Focus is the only enabled tab
- **THEN** the floating bar shows the "Add session" button as its solo action

## ADDED Requirements

### Requirement: Adding from Focus opens the new-session sheet over Focus
Activating "Add session" on Focus SHALL open a blank new-session sheet over Focus, without navigating away, at every window width. Closing the sheet SHALL return to Focus as it was. Focus SHALL NOT offer creating a top-level task; tasks are added from the Tasks tab.

#### Scenario: Create a session from Focus
- **WHEN** the user taps "Add session" on Focus, enters a name and saves
- **THEN** the session is listed on Focus and the user is still on Focus

#### Scenario: Add twice
- **WHEN** the user closes the sheet and taps "Add session" again
- **THEN** a new blank sheet opens again

#### Scenario: Closed without saving
- **WHEN** the user opens the sheet and dismisses it without saving
- **THEN** no session is created

## REMOVED Requirements

### Requirement: Adding from Focus opens the task creation sheet over Focus
**Reason**: The Focus add action now creates a session (#517); Focus is where sessions are made, and tasks are made in Tasks.
**Migration**: Add tasks from the Tasks tab. Editing a task and adding a subtask from Focus are unchanged.
