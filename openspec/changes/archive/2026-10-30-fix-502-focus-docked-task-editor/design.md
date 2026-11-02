# Design

## Context

Tasks and Routines dock their editors with the same pieces: the screen lays out a `Row` with the weighted `Scaffold` and a `DockedEditorPane`; `rememberSheetPlacement()` decides `DockedPane` vs `BottomSheet`; `animatedRailClearance` / `animatedDockedContentBias` slide the content aside; `onContentBlockTopChange` aligns the pane with the content block. Since #501, docking doesn't touch navigation: Back is handled by the docked sheet itself, and the form state lives in the view model.

Focus already reuses the Tasks editor: `FocusTaskEditor` creates its own `TasksViewModel` on first use and always renders `TaskEditor` with `SheetPlacement.BottomSheet`. `FocusScreen` draws it after its `Scaffold`, and after `PlanTasksSheet`, so an edit opened from planning stacks over the plan sheet. `FocusRoute` already carries `EDITOR_MAIN_ROUTE_METADATA`, but no editor route supports it.

The editor inside the running session (`FocusSessionScreen`) has its own view model and stays out of scope.

## Goals / Non-Goals

**Goals:**
- Focus docks with the same components and motion as Tasks, not a Focus-specific pane.
- The Plan tasks sheet keeps its stacking behaviour.

**Non-Goals:**
- Extracting a shared "screen with docked editor" scaffold for all three tabs. Tempting, but it touches Tasks and Routines; worth its own refactor once #468's layout contract settles what the shared shape is.

## Decisions

### Placement follows the window, except when opened from the plan sheet

`FocusTaskEditor` takes a `placement`. `FocusScreen` passes `rememberSheetPlacement()` for editors opened from the agenda, subtask actions and "Add Task", and `BottomSheet` while `planSheet` is open. The target carries where it came from only implicitly: if the plan sheet is open when the editor opens, it's a plan-sheet edit. That avoids a new field on `TaskEditorTarget` and matches the existing draw order, where the editor already sits above the plan sheet.

Alternative considered: dock even from the plan sheet. Rejected: the plan sheet is modal, so a pane beside a scrimmed screen would sit under or beside a sheet, and closing the editor would no longer land back in planning.

### Same pane, same signals as Tasks

`FocusScreen` adopts the `Row` + `DockedEditorPane` structure, rail clearance, content bias and content-block top from `TasksScreen`, and renders the docked `TaskEditor` inside the pane with a movable content holder so moving between pane and sheet when the window crosses 1200 dp keeps the draft (the same reason `TasksScreen` uses `movableContentOf`). `rememberFrozenWhileHidden` keeps the exiting pane from recomposing into a blank form.

Like the other tabs after #501, Focus doesn't report the docked state to navigation, so the back stack and `NavDisplay`'s scene never change when the editor docks, and it can't blink.

### Selection and re-tap

The agenda shows the edited task as selected, as Tasks does with `selectedTaskId`. Tapping the task already in the pane closes the editor, mirroring `TasksScreen`'s `editorDismissRequestKey`; tapping a different task replaces the target and `FocusTaskEditor` opens it (its `LaunchedEffect(target)` already re-opens per target).

### The session docks in the same pane, one item at a time

Added after review. The session route stays on Focus's back stack as before (the pill already switches to Focus and pushes it), so Back still closes it. On Large and up the app-level overlay stops drawing it, and `FocusScreen` shows it in its docked pane instead, with its own `FocusViewModel` instance as the sheet had (the state comes from the repositories both observe). `focusPane()` decides what the pane holds: a task when one is open, else the session. The one opened last wins: a task opening closes the session (`navigator.pop()`); the session opening, or being found open when Focus is composed afresh after the pill switched tabs, closes a docked task through its guarded dismiss. A subtask tapped in the docked session is handed to Focus's own editor, so it takes over the pane like any task.

Alternative considered: docking the session beside whichever tab is showing. Rejected by the user in favour of switching to Focus, which keeps one pane per screen and avoids two panes squeezing a list.

## Risks / Trade-offs

- [Duplicated docking glue in a third screen] → kept as a straight copy of the Tasks pattern so a later extraction is mechanical; noted as a follow-up for #468.
- [Two `TasksViewModel` instances (Tasks tab and Focus) editing the same task] → unchanged from today's sheet; both read the same repository.
- [The pane's top alignment depends on a header measured a frame later, which layoutlib doesn't render] → covered by manual validation and, if needed, an instrumented check like `DockedEditorPaneTest`.
- [Lazy `TasksViewModel` creation making the first open slow on a cold start] → observed 4 s on a heavily loaded emulator; re-measure on a single emulator, and only treat it as a problem if it reproduces.
