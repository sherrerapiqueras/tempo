# Spec Delta

## Purpose

Lets a repeating task stop on a chosen date, so its occurrences and reminders end without the user having to remember to stop them.

## ADDED Requirements

### Requirement: The periodicity section offers an end date
When a top-level task has a periodicity selected, the task editor SHALL show an end-date option as the last row of the periodicity section, below any sub-selectors for that periodicity. The editor SHALL NOT show it when no periodicity is selected, or for a subtask.

#### Scenario: Below the weekly sub-selectors
- **WHEN** the user selects Weekly in the task editor
- **THEN** the end-date option appears below the interval selector and the weekday selector

#### Scenario: Below the interval when there are no other sub-selectors
- **WHEN** the user selects Daily in the task editor
- **THEN** the end-date option appears directly below the interval selector

#### Scenario: No periodicity selected
- **WHEN** the user clears the periodicity
- **THEN** the end-date option is no longer shown

### Requirement: The user can add, change and clear an end date
With no end date set, the option SHALL be an "Add end date" action that opens a date picker. With one set, it SHALL show the date as "Until <date>", SHALL reopen the picker when tapped, and SHALL offer a labelled clear action. Every target SHALL be at least 48 dp tall.

#### Scenario: Add an end date
- **WHEN** the user taps "Add end date" and confirms 31 March in the picker
- **THEN** the option shows "Until 31 Mar" and, after saving, the task keeps that end date

#### Scenario: Change an end date
- **WHEN** the user taps "Until 31 Mar" and confirms 15 April
- **THEN** the option shows "Until 15 Apr"

#### Scenario: Clear an end date
- **WHEN** the user activates the clear action on "Until 31 Mar"
- **THEN** the option returns to "Add end date" and, after saving, the task has no end date

#### Scenario: Dismiss the picker
- **WHEN** the user cancels the date picker
- **THEN** the end date is unchanged

### Requirement: The end date cannot precede the first remaining occurrence
The date picker SHALL only allow dates on or after both today and the task's reminder date, and it SHALL open on the month of the earliest allowed date when no end date is set yet.

#### Scenario: Reminder in the future
- **WHEN** the task's reminder is on 10 June and the user opens the end-date picker in March
- **THEN** the picker opens on June and dates before 10 June cannot be selected

#### Scenario: End date on the reminder date
- **WHEN** the user picks the same date as the reminder
- **THEN** the date is accepted

#### Scenario: Stored end date is no longer allowed
- **WHEN** the end date has passed or falls before the reminder, and the user opens the picker
- **THEN** that date is not selected, and the picker can't be confirmed until an allowed date is chosen

### Requirement: The end date follows the recurrence it belongs to
The editor SHALL keep the end date when the user switches between periodicities or changes the interval or days. It SHALL discard the end date when the periodicity or the reminder is cleared, and when the user moves the reminder to a date after the end date.

#### Scenario: Switch periodicity
- **WHEN** a Daily task has an end date and the user switches it to Weekly
- **THEN** the end date is kept

#### Scenario: Clear the periodicity
- **WHEN** the user clears the periodicity of a task with an end date and selects a periodicity again
- **THEN** the option shows "Add end date"

#### Scenario: Move the reminder past the end date
- **WHEN** the end date is 31 March and the user moves the reminder to 5 April in the editor
- **THEN** the option returns to "Add end date"

### Requirement: The end date is inclusive and limits occurrences
The end date SHALL be a calendar date in the device's time zone with no time of day. An occurrence on the end date SHALL happen, and the system SHALL NOT create an occurrence on a later date.

#### Scenario: Occurrences on the end date
- **WHEN** a task repeats every hour and its end date is today
- **THEN** occurrences continue through the end of today and none is created for tomorrow

#### Scenario: Saving a past reminder that the end date stops
- **WHEN** at 10:00 the user saves a daily task with a reminder at 08:00 today and an end date of today
- **THEN** the user is told the reminder is in the past and won't fire

#### Scenario: Interval steps over the end date
- **WHEN** a task repeats every 7 days from 1 March with an end date of 20 March
- **THEN** its last occurrence is on 15 March

### Requirement: Completing the last occurrence ends the series
When the user completes a repeating task and its next occurrence would fall after the end date, the system SHALL complete the task and SHALL NOT create another occurrence or schedule another reminder.

#### Scenario: Complete the last occurrence
- **WHEN** a daily task with an end date of 31 March is completed on its 31 March occurrence
- **THEN** the task moves to completed and no new occurrence appears

#### Scenario: Complete from a notification after the reminder has moved
- **WHEN** a daily task's notification fired for 1 March, its reminder has since moved to 3 March, and the user completes it from that notification on 1 March
- **THEN** the next occurrence is on 2 March

#### Scenario: Complete an earlier occurrence
- **WHEN** the same task is completed on its 30 March occurrence
- **THEN** a new occurrence for 31 March is created, with the same end date

#### Scenario: Uncheck the last occurrence
- **WHEN** the user unchecks a completed last occurrence
- **THEN** the task is incomplete again, does not repeat, and no occurrence is created

### Requirement: The end date survives backup and restore
A backup SHALL include each task's end date, and restoring it SHALL bring that date back. A backup written before end dates existed SHALL restore with no end date on its tasks.

#### Scenario: Restore a backup with an end date
- **WHEN** a backup containing a task that ends on 31 March is restored
- **THEN** the restored task ends on 31 March

#### Scenario: Restore an older backup
- **WHEN** a backup with no end-date field is restored
- **THEN** the restore succeeds and its repeating tasks repeat with no end date

### Requirement: Existing tasks keep repeating
Upgrading the app SHALL leave every existing task with no end date, and its recurrence SHALL behave as before.

#### Scenario: Upgrade with repeating tasks
- **WHEN** the app is upgraded on a device with repeating tasks
- **THEN** those tasks are preserved with no end date and continue to repeat
