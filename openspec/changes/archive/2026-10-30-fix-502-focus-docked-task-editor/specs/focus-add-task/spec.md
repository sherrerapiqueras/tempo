# Spec Delta

## MODIFIED Requirements

### Requirement: Adding from Focus opens the task creation sheet over Focus
Activating "Add Task" on Focus SHALL open the same blank task editor Tasks opens, without navigating away from Focus: as a sheet over Focus below Large, and docked beside the Focus agenda from Large up. Closing the editor SHALL return to Focus as it was.

#### Scenario: Create a task from Focus
- **WHEN** the user taps "Add Task" on Focus, enters a title and closes the editor
- **THEN** the task is created exactly as if it had been added from Tasks, and the user is still on Focus

#### Scenario: Add twice
- **WHEN** the user closes the editor and taps "Add Task" again
- **THEN** a new blank editor opens again

#### Scenario: Large window
- **WHEN** the window is 1280 dp wide and the user taps "Add Task" on Focus
- **THEN** the blank editor docks beside the Focus agenda instead of opening as a sheet
