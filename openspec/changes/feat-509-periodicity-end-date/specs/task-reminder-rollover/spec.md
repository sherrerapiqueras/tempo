# Spec Delta

## ADDED Requirements

### Requirement: Rollover stops at the end date
When an overdue periodic task has no valid next occurrence and its next future occurrence would fall after its end date, the system SHALL NOT create a next occurrence. The overdue task SHALL remain incomplete with its reminder date, SHALL no longer repeat, and SHALL NOT be scheduled again.

#### Scenario: Last occurrence goes overdue
- **WHEN** a daily task with an end date of 31 March is still incomplete after its 31 March reminder
- **THEN** the task stays incomplete and overdue, and no occurrence is created for 1 April

#### Scenario: Device off past the end date
- **WHEN** a daily task with an end date of 10 March has a 5 March reminder and rollover first runs on 15 March
- **THEN** no occurrence is created, including for the dates between 6 and 10 March, and the task stays overdue on 5 March

#### Scenario: Fast-forward stays within the end date
- **WHEN** a daily task with an end date of 31 March has a 09:00 reminder on 5 March and rollover first runs at midday on 15 March
- **THEN** one occurrence is created for the next future date, 16 March, with the same end date

#### Scenario: Same task is processed again
- **WHEN** a task whose series has ended through rollover is processed again
- **THEN** no occurrence is created and its reminder date does not move
