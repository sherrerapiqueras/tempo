# Design

## Context

`TasksScreen` widens the content from 840 dp to 1280 dp when a category has sections, and `TasksContent` swaps its `LazyColumn` for a `LazyVerticalStaggeredGrid` from a 900 dp pane. The one-column branch already handles sections in full, because it is what phones and a docked editor use. See proposal.md for the motivation.

## Goals / Non-Goals

**Goals:**

- One layout path for the Tasks list at every width.

**Non-Goals:**

- A replacement use for the free width on wide windows (#468).

## Decisions

**Remove the grid rather than keep it unused.** With no 900 dp pane left, the grid can never show, so `TasksContentSectionGrid.kt`, its item keys, `CompletedBlock` and the `isTwoColumn` plumbing go. If #468 brings columns back they will be category columns, a different layout, so this code wouldn't be the starting point. Alternative considered: two tidier columns, with "No section" as a column-width block. That would need every category to widen at that width, or the list would still jump, and the user chose one column.

**Keep the shortcut group at column width.** It already sits inside the capped content, so removing the 1280 dp width is enough. A one-column list is longer, so jumping between sections is worth more. Alternative considered: removing it so nothing at all moves when the first section appears. The user preferred to keep it and accept the panel moving down by one row.

## Risks / Trade-offs

- [The list panel still moves down when the first section appears at Large and up] → Accepted, as it is one row and vertical only. The spec's width and position requirement is about the horizontal layout.
- [Long lists lose the density two columns gave on wide windows] → Accepted; the shortcut group covers navigation and #468 tracks richer layouts.
