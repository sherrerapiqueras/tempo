# Spec Delta

## ADDED Requirements

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
