# Proposal

## Why

On a Large window (≥ 1200 dp) with the task editor docked beside the list, the seams are rough (seen on a 1280 dp tablet with one "Inbox" category):

- The expanded rail's app title is not bounded by the rail. "Tempo (Debug)" is about 265 dp wide in the 220 dp rail, so it spills into the screen content. Once the editor docks, the list's readable column is no longer centred with spare margin, it starts right at the rail clearance, and the title runs into the selected "Inbox" chip.
- The list's rounded content block runs flush into the docked editor pane: no gutter, and its top-end corner merges with the pane's edge.
- The pane is a floating card that starts 12 dp below the status bar with a shadow, while the content block starts below the category chips, rounds only its top corners and runs to the bottom edge. The two white surfaces do not line up.

## What Changes

- The expanded rail title is kept within the rail: single line, auto-sized down to fit the rail width.
- The docked editor pane moves into one shared `DockedEditorPane` (Tasks and Routines) that:
  - leaves a gutter between the content block and the pane equal to the rail-to-content gap (16 dp) and uses the same margin at the window's end edge;
  - starts the pane's surface at the same height as the screen's content block (below the chip row / day filter);
  - grows and shrinks the gutter with the pane, so docking still glides.
  - The screen's `Row` paints the tinted background, so the gutter and margins aren't the white root surface showing through.
- The docked sheet surface matches the content block: top corners rounded, square bottom running to the window's bottom edge, no shadow.
- While the editor is docked the readable column slides (animated) to the pane's side of the free space, so the gutter stays 16 dp at Extra-large widths too instead of widening to whatever the centring leaves.

### Non-goals

- No change to breakpoints, the readable max width (840 dp), or which editors dock.
- No change to sections or other Tasks content.

## Capabilities

### Modified Capabilities

- `adaptive-window-classes`: adds the docked editor seam requirement; docking keeps gliding.

## Impact

- UI: `core/ui/adaptive/DockedEditorLayout.kt` (`DockedEditorPane`, gutter), `core/ui/components/TempoModalSheet.kt` (docked shape and shadow), `core/ui/navigation/NavigationComponents.kt` (`adaptiveScreenContentLayout` bias), `core/ui/navigation/PersistentFloatingBar.kt` (rail title), `TasksScreen`/`TasksContent`, `RoutinesScreen`/`RoutinesContent` (content block top reported to the pane).
- Screenshot tests: the `AdaptiveTaskSheet*` references are replaced by `DockedTasksScreen*` (the list and the docked editor side by side) at 1280 dp and 1600 dp, light and dark. The pane's top alignment depends on a header measured one frame later, which layoutlib's single-frame render does not reflect, so it is covered by the new instrumented `DockedEditorPaneTest` instead.
