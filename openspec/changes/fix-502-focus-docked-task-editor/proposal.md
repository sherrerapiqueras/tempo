# Proposal

## Why

On a Large window, Tasks and Routines open their editors in a pane docked beside the list. Focus still opens a task, and a new task from its add action, as a modal bottom sheet over a blurred screen ([#502](https://github.com/sherrerapiqueras/tempo/issues/502), part of [#468](https://github.com/sherrerapiqueras/tempo/issues/468)). This breaks the rule that editors dock from Large up. Reproduced on the Pixel Tablet emulator in landscape (1280 dp).

## What Changes

- On Large and up, opening a task from the Focus agenda, adding a subtask, or using "Add Task" on Focus docks the task editor beside the agenda, exactly as in Tasks: the agenda stays visible and usable, the opened task's card is shown as selected, and opening another task swaps what the pane shows.
- Below Large, Focus keeps its bottom sheet.
- A task opened from the Plan tasks sheet still opens as a sheet over it, so closing the editor returns to planning.

### Non-goals

- The focus session stays a sheet at every width. On a large window the session pill in the rail already brings you back to it; docking the session beside any tab is left for a later sub-issue of #468 if it proves worth it.
- No change to the Plan tasks sheet, or to the editor opened from inside the session.
- No change to Tasks or Routines docking, or to the pane's size, seams or motion.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `adaptive-window-classes`: states that Focus docks its task editor from Large up, like the other tabs, with the Plan tasks exception.
- `focus-add-task`: "Add Task" on Focus opens the task editor in the placement the window calls for (docked from Large up) instead of always a sheet.

## Impact

- UI: `features/focus/presentation/FocusScreen.kt` (screen `Row` with `DockedEditorPane`, rail clearance and content bias as in Tasks), `FocusEditors.kt` (`FocusTaskEditor` takes a placement), `FocusContent.kt` (selected card).
- Navigation: a Focus editor route alongside `TasksEditorRoute` / `RoutinesEditorRoute` in `core/ui/navigation/Navigation.kt`, reported from `FocusDestination` in `NavigationDestinations.kt`.
- Depends on [#501](https://github.com/sherrerapiqueras/tempo/issues/501) (`fix-501-docked-editor-blink`) so the new docking path doesn't blink.
- Tests: Focus screenshot references at Large widths (docked editor beside the agenda); unit tests for the Focus view model's editor state if it changes; manual validation on tablet, desktop window and phone.
