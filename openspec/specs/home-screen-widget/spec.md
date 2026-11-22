# home-screen-widget Specification

## Purpose
TBD - created by archiving change feat-today-widget. Update Purpose after archive.

## Requirements

### Requirement: Today widget shows the Focus day
The home-screen widget SHALL list the tasks of the current local day as Focus defines them (due today, then overdue and still open), uncompleted before completed, across all categories. Each row SHALL show the task title, and a supporting line with its time (or an "Overdue" label for an item carried over from a previous day) and its category.

#### Scenario: Day with tasks
- **WHEN** two tasks are due today at 09:00 and 15:00 and one task from yesterday is still open
- **THEN** the widget lists the 09:00 task, the 15:00 task, then yesterday's task labelled Overdue

#### Scenario: Nothing left
- **WHEN** the day has no tasks
- **THEN** the widget shows an empty message and its add action

### Requirement: Widget actions
Tapping a row's check SHALL toggle that task's completion with the same effects as completing it in the app. Tapping a row SHALL open that task in the app. The add action SHALL open the new-task sheet.

#### Scenario: Complete from the widget
- **WHEN** the user taps the check of an open task
- **THEN** the task is completed and a periodic task creates its next occurrence as it would in the app; a task due today stays with the done check, and an overdue one leaves the list, as it leaves Focus's day

#### Scenario: Open a task
- **WHEN** the user taps a row's title
- **THEN** the app opens with that task's editor

### Requirement: Widget adapts to its size
The widget SHALL be resizable. Below 110 dp of width it SHALL show a quick-add tile with the count of open tasks; below 110 dp of height (and wider) it SHALL show one line with the day's progress and the next open task beside the add action; below 180 dp of height it SHALL show a compact header (day, progress, add) above the list; otherwise it SHALL show a header with the day, the date, the completed-of-scheduled count and the add action above the list.

#### Scenario: One row high
- **WHEN** the widget is 4×1
- **THEN** it shows "Today", the progress and the next open task's title, with the add action

#### Scenario: Previously placed 1×1 widget
- **WHEN** a widget placed before this change is 1×1
- **THEN** it shows the quick-add tile and opens the new-task sheet when tapped

### Requirement: Widget stays current
The widget SHALL reflect task changes made in the app, from notification actions or from the widget itself, and SHALL show the new day after local midnight, without the user opening the app. Its text SHALL be in the language chosen for the app, even when the system language differs.

#### Scenario: Completed from a notification
- **WHEN** a task is marked done from its reminder notification
- **THEN** the widget shows it as done

#### Scenario: App language differs from the system
- **WHEN** Tempo is set to Spanish on a phone in English
- **THEN** the widget reads "Hoy", "Atrasada" and the Spanish date

#### Scenario: Day rolls over
- **WHEN** local midnight passes
- **THEN** shortly after it the widget shows the new day's tasks, also when the app's process is not running

### Requirement: Widget follows Tempo's visual language
The widget SHALL use Tempo's two-block layout (tinted header over a surface block), present the task rows as one connected group of cards, use Tempo's task check, and colour the add action, its one main action, with `primary` (the tinted strip derives from `secondaryContainer`, so the colour the app uses for "add" would not stand out on it). It SHALL follow the system's dynamic colours where available and Tempo's palette otherwise, in light and dark.

#### Scenario: Dynamic colour unavailable
- **WHEN** the device does not support dynamic colour
- **THEN** every element of the widget uses the matching role of Tempo's light or dark palette
