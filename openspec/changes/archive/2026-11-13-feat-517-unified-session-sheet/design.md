# Design

## Context

The session sheet is its own navigation entry with its own `FocusViewModel`; what it shows crosses to it through `FocusSessionRepository` (`activeSession`, `previewTaskId`). The saved-session form was instead an overlay owned by the Focus screen's view model, which is why the two could not be one surface.

## Goals / Non-Goals

**Goals:** one sheet for setting up and running; no close-and-reopen on Start; rounds visible.

**Non-Goals:** persistence changes; docking changes.

## Decisions

### The session being set up lives beside the task preview
`FocusSessionRepository.setupSessionId` (in memory, like `previewTaskId`): `0` for a new session, an id for a saved one, `null` for none. Setting one clears the other. The sheet's subject key is the previewed task, else the session being set up (`session:<id>`), else the running timer — and a saved session's key is the same before and after it starts, which is what keeps the sheet open across Start. A new session has no id until it is saved, so a sheet latched on `session:0` adopts the first real key that follows.

### The draft stays in the sheet
Name and numbers are typed into state the sheet holds (`SessionEditorForm`), as before. Start on a session sends `SaveSession(draft, startNow)`; Start on a task sends its length, rest and rounds. Closing an edited saved session still saves it. That write runs `NonCancellable`: the sheet's view model is cleared as the sheet closes.

### A cycle without a saved session
`SessionCycle.savedSessionId` becomes nullable. A task run with rounds > 1 carries a cycle with no saved session; everything that asked "is this a saved session's timer?" now asks `savedSessionId != null` rather than `cycle != null`. Stored timers written by the previous build read back unchanged.

### One pre-start layout, animated into the running one
The body keeps one ring. Below it, the setup rows sit in an `AnimatedVisibility` tied to "not running", and the controls in an `AnimatedContent` keyed on running, so Start shrinks the setup away and swaps the buttons in place while the ring begins to move.

### Double-save guard
With the form no longer its own piece of state, "only an open sheet can save" becomes a flag held for the length of the write.

### The dial is drawn from a Material shape's outline
`SessionDial` takes `MaterialShapes.Cookie12Sided` / `Flower` as a `Shape`, fills its outline and traces progress along the same path with `PathMeasure`. Only Material 3 and Compose graphics APIs are used; no new dependency. It breathes through an infinite transition while running — which Compose tests do not wait on — and eases back to rest when paused. The running card uses the same dial, small.

### One linked-task block, three sets of actions
`LinkedTaskRow` plus the task's metadata pills, fed from one resolved entry: the timer's or previewed task's entry, or — while a saved session is being set up — the entry for the task chosen in the form (`UiState.taskEntry`, which also looks in the linkable tasks, since a chosen task need not be on today). The chip always sends `EditTask`, which the session route already answers with the task editor as a sheet over it. Unlinking is `form.linkedTaskId = null` for a session being set up and `UnlinkPreviewTask` for a sheet opened on a task, which sets up a new session in its place; the sheet's latch lets go for that hand-over. Neither is offered once running.

### Height follows content
The body is `fillMaxWidth` rather than `fillMaxSize`; the modal sheet already wraps its content up to its maximum height and scrolls beyond it.

## Risks / Trade-offs

- [Up next now mixes tasks and sessions] → session cards lead their metadata with a timer icon and their rounds/rest, tasks with priority/category/time; tasks come first.
- [Sheet state restored after the session it was setting up is deleted] → its key resolves to nothing and the sheet closes, as it does for a finished timer.
