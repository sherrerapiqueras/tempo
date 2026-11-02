# Proposal

Fixes [#510](https://github.com/sherrerapiqueras/tempo/issues/510), part of [#468](https://github.com/sherrerapiqueras/tempo/issues/468).

## Why

On a large window the Tasks list rearranges itself the moment a category gets its first section: the 840 dp centred column grows to the free width, shifts towards the rail, loses its right margin, stretches unsectioned tasks across two columns and gives each section half the width. Nothing about the window changed, so the list reads as broken.

## What Changes

- A category with sections keeps the same single list column as one without: up to 840 dp wide, in the same position, with the same margins, at every window width.
- Unsectioned and sectioned tasks share one card width.
- **BREAKING** (behaviour): sections no longer flow into two columns on wide panes. The two-column requirement is removed.
- The Large-and-up section shortcut group stays, at the list column's width, and scrolls sideways when its segments don't fit. It is now the only thing that moves when the first section appears: it pushes the list down by its own height.

## Non-goals

- Showing categories side by side, or letting the user switch layouts. #468 floats both; they are separate work.
- Using the free width on very wide windows in any other way.
- Changing section headers, drag and drop, sorting, the Completed block, or the layout below Large width.
- Changing the docked editor's behaviour, other than the list no longer switching layout when it opens.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `task-section-list-layout`: removes the two-column flow, adds a requirement that sections don't change the list's position or width, and states the shortcut group's width.

## Impact

- `TasksContent.kt`, `TasksContentSectionShortcuts.kt`, `TasksScreen.kt`, `NavigationComponents.kt`, `SectionListLayout.kt`; `TasksContentSectionGrid.kt` is deleted.
- `SectionScrollSpyTest` loses its grid-key test.
- `TasksSectionedScreenshots` references change on purpose at tablet width.
- No data, navigation or dependency changes.
