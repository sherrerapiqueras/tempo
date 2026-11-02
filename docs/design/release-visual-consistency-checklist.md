# Release Visual Consistency Checklist

Quality gate for 1.0 and every release candidate afterwards. Run the full pass before cutting a
release; run the relevant surface pass when reviewing PRs that touch UI. Keep this document short
enough to actually use — when a criterion stops being checked, delete it or fix why.

Related issues: [#21](https://github.com/mandrecode/tempo/issues/21) (this checklist),
#163 (adaptive quality), #79 (expressive loading), #24 (shared editor patterns), #34 (interaction spec).

---

## 1. Configurations to review

Every surface pass below must be exercised in at least:

- [ ] **Compact window** (phone portrait, < 600dp): bottom navigation + modal bottom sheets
- [ ] **Medium window** (≥ 600dp): floating navigation rail, top bar without settings action
- [ ] **Expanded window** (≥ 1200dp): rail with labels, docked editor pane (412dp), no top-bar title
- [ ] **Light and dark theme**, both **Dynamic** and **Tempo** color schemes
- [ ] **Largest font scale** (Settings → Display → Font size max): no clipped or overlapping text
- [ ] **Resize across breakpoints** (foldable/split-screen): open editor survives the transition,
      nothing flashes or double-scrims (see PR #156 and PR #164 for past regressions in this area)

Interaction checks are part of the pass: mark items complete and incomplete, expand/collapse,
drag to reorder, open/dismiss every editor and dialog — not just static screenshots.

## 2. Shared criteria (all surfaces)

### Typography
- [ ] Every text uses a `MaterialTheme.typography` style or a semantic token from
      [`Type.kt`](../../app/src/main/java/com/mandrecode/tempo/core/ui/theme/Type.kt)
      (`topBarTitle`, `cardTitle`, `sectionHeader`, `dialogTitle`, …)
- [ ] No `.copy(fontWeight = …)` / `fontSize =` overrides in feature composables — if a new style
      is needed, add a semantic token first
- [ ] Same role ⇒ same token across tasks, routines, and settings (card titles, section headers,
      metadata labels, dialog actions)

### Spacing & alignment
- [ ] Screen gutters, list item spacing, and card content padding match across tabs (see gap G3)
- [ ] Values come from the `Spacing` scale (2/4/8/16/24/32/64dp) or Material's own tokens, such
      as the 12dp between a list item's leading element and its content; other values
      (14, 18dp…) need a reason
- [ ] Titles, checkboxes, and trailing icons align on a shared axis inside cards; group headers
      align with card content edges

### Color & state presentation
- [ ] Only `MaterialTheme.colorScheme` roles or theme-aware palette entries
      ([`ColorPalette.kt`](../../app/src/main/java/com/mandrecode/tempo/core/ui/theme/ColorPalette.kt));
      no raw hex in composables
- [ ] Completed/disabled alpha treatments identical for the same role across surfaces
- [ ] Selected-card style matches everywhere (`SelectableCard` helpers: `selectedContainerColor`,
      `selectableCardElevation`) — tasks, habits, chains, quit habits
- [ ] Loading, empty, and error states present and visually consistent on every list surface

### Editors (bottom sheets / docked pane)
- [ ] Task and habit editors share structure: title field (`inputTitle`), description
      (`bodyLarge`), section order, footer actions, horizontal padding of 16dp on compact
      windows and 24dp on larger ones
- [ ] Same editor renders correctly as bottom sheet (compact) and docked pane (expanded);
      dismiss, discard-confirm, and keyboard behavior equivalent in both placements
- [ ] Top-origin sheets (category edit) keep their direction and match modal styling

### Dialogs
- [ ] All confirm dialogs follow the shared pattern: `dialogTitle`, `dialogAction`, error-colored
      confirm `Button`, `OutlinedButton` cancel, pressable-corner animation, haptics
- [ ] Dialog copy names the right entity and is translated in every locale

### Icons & touch targets
- [ ] Interactive elements ≥ 48dp; icons sized 20/24dp consistently for the same role
- [ ] Every interactive icon has a `contentDescription`; decorative icons explicitly `null`
- [ ] Habit icon + color render consistently across card, chain row, editor, and picker

### Motion & interaction
- [ ] Card completion morph (corner 32→24dp, scale, haptic) feels identical for tasks and habits
- [ ] Durations/springs come from `TempoMotionTokens` or the established spring specs; no ad-hoc
      `tween(300)` drift in new code
- [ ] Drag-and-drop (tasks, subtasks, categories) uses the same lift/alpha/haptic language

## 3. Surface passes

### Tasks
- [ ] Category chip row: selected/unselected styles, counts, reorder, overflow scrolling
- [ ] Card states: active, completed (strikethrough + alpha + offset), selected (docked editor),
      with/without description, subtasks expanded/collapsed, metadata row variants
- [ ] Group headers and completed-separator pill; sort modes (manual/date/priority) keep stable layout
- [ ] Empty state and loading state centered at all window sizes

### Routines
- [ ] Day filter row: selected day treatment, today marker, divider
- [ ] Card families side by side: habit, chain (collapsed/expanded), quit habit — same geometry,
      padding, title/metadata typography; habit color tints stay legible in both themes
- [ ] Timeline gutter (time labels + rail) aligns across mixed scheduled/unscheduled content
- [ ] Quit-habits separator pill matches tasks' completed separator treatment
- [ ] History dots/streak badges consistent between cards and chains

### Settings
- [ ] Section cards (28dp radius, `surfaceContainer`), item paddings, divider indents uniform
- [ ] Chip selectors (theme, color scheme) share height/padding/icon sizing
- [ ] Top bar behavior per tier: collapsing large title (compact), fixed title (rail), status
      inset only (expanded) — no jumps when navigating in/out
- [ ] External-link items (notifications, language) show the same trailing affordance

### Onboarding
- [ ] Typography scales between compact/expanded variants without ad-hoc weights (gap G10)
- [ ] Page indicators, buttons, and setup controls match the app's chip/button language
- [ ] Replay from Settings renders identically to first run

### Navigation chrome
- [ ] Bottom bar ↔ rail transition: selected tab treatment, badge/label behavior, add-action
      parity between tasks and routines (gap G13)
- [ ] Snackbars use `ExpressiveSnackbarHost` on every surface that shows one; position clears
      floating bar/rail at each tier

---

## 4. Known gaps — fix before 1.0

Found in the 2026-07 audit. Check off when fixed (with PR link).

### High
- [x] **G1 — Habit delete dialog says "task".**
      `DeleteHabitConfirmDialog` reuses `delete_task_message_prefix`/`_suffix`, so deleting a
      habit reads "Are you sure you want to delete task *<habit>*?". Add habit-specific strings
      (all locales).
      (`features/routines/presentation/components/dialogs/DeleteHabitConfirmDialog.kt`)
      Fixed in [#170](https://github.com/mandrecode/tempo/pull/170).
- [x] **G2 — Priority colors are hardcoded pastels, not theme-aware.**
      `Priority.color` returns fixed hex (`#FF6961`, `#FDFD96`, `#77DD77`) with no light/dark
      variants, unlike the habit `ColorPalette` (tone 40/80 pairs). Pastel yellow as
      `selectedContentColor` in the priority selector is a contrast failure risk on light theme.
      Move priorities to theme-aware palette entries.
      (`core/ui/util/PriorityExtensions.kt`, `features/tasks/presentation/components/TaskBottomSheetFormSections.kt:399`)
      Fixed in [#173](https://github.com/mandrecode/tempo/pull/173).

### Medium
- [x] **G3 — List frame differs between tabs.** Tasks list: 20dp gutters, 8dp top, 8dp item
      spacing. Routines list: 16dp gutters, 16dp top, 12dp spacing. Card edges visibly shift when
      switching tabs. Pick one frame (or document why they differ) and encode it as a shared
      constant. (`TasksContent.kt:181`, `RoutinesContent.kt:153`)
      Fixed in [#175](https://github.com/mandrecode/tempo/pull/175).
- [x] **G4 — Filter rows diverge.** CategoryChipRow is 84dp tall with `categoryChipSelected`
      (titleMedium bold); DayFilterRow is 64dp with `filterChipSelected` (labelLarge bold), plus a
      hairline divider tasks lacks. Align heights/typography or record the intended difference.
      Fixed in [#176](https://github.com/mandrecode/tempo/pull/176).
- [x] **G5 — Top bar title treatment inconsistent.** Tasks/routines: `topBarTitle`
      (headlineMedium bold, primary color). Settings: headlineSmall/displayMedium **normal**,
      onSurface, collapsing. Decide the title hierarchy for 1.0 and document the settings
      exception if kept. (`TempoTopBar.kt`, `SettingsScreen.kt:139-155`)
      Documented in [#177](https://github.com/mandrecode/tempo/pull/177).
- [x] **G6 — Spacing tokens unused.** `LocalSpacing`/`TempoSpacing` exist but ~6 files use them;
      the rest hardcode dp (including off-scale 10/12/20/28dp). Either adopt tokens in shared
      components + new code, or delete the system so it stops implying a standard that isn't
      enforced. (`core/ui/theme/Spacing.kt`)
      Fixed in [#179](https://github.com/mandrecode/tempo/pull/179): removed the dead values and
      documented the scope of each spacing system instead of forcing adoption everywhere.
- [x] **G7 — Confirm dialogs are copy-paste septuplets.** Seven near-identical AlertDialog
      implementations (delete task/habit/category/completed, discard, clear reminders, empty
      chain). Consistent today, but every future edit risks drift. Extract a shared
      `TempoConfirmDialog`. (relates to #24)
      Fixed in [#185](https://github.com/mandrecode/tempo/pull/185).
- [x] **G8 — Preview coverage gaps.** No `src/debug` previews for task dialogs
      (`DeleteTaskConfirmDialog`, `CategoryDialog`, `DeleteCategoryConfirmDialog`,
      `DeleteCompletedConfirmDialog`), `ColorPicker`, `IconPicker`, `DayOfWeekSelector`,
      `TempoTopBar`, or the date/time picker dialogs — these can regress invisibly.
      Fixed in [#187](https://github.com/mandrecode/tempo/pull/187).
- [x] **G9 — Completion checkbox geometry differs tasks vs habits.** Task: 48dp box, radius
      24→16, bordered, onSurface 5% fill, scale 1.1. Habit: radius 28→16, color-tinted 12% fill,
      no border, scale 1.05, 56dp min row height. Habit color identity is intentional; the
      geometry/scale split probably isn't. Unify the non-color parameters.
      (`TaskCard.kt:157-175`, `HabitCards.kt:117-151`)
      Fixed in [#188](https://github.com/mandrecode/tempo/pull/188): unified checkbox scale to 1.1
      across `TaskItem`, `HabitItem`, `HabitCompletionCheckbox`, and `ChainHabitCheckbox`.

### Low
- [x] **G10 — Ad-hoc font weights in settings and onboarding.** Replace
      `.copy(fontWeight = …)` with semantic tokens (`SettingsScreen.kt:139,152-154`,
      `OnboardingContent.kt:179,299,390`, `OnboardingSetupPage.kt:38`).
      Fixed in [#178](https://github.com/mandrecode/tempo/pull/178).
- [x] **G11 — Loading state duplicated.** Identical 30-line spinner block in `TasksContent` and
      `RoutinesContent`; extract one component (and upgrade it once for #79).
      Fixed in [#189](https://github.com/mandrecode/tempo/pull/189): extracted
      `TempoLoadingIndicator`.
- [x] **G12 — Chain card title uses `dialogTitle`.** A card title styled with the dialog token
      couples two roles; introduce `chainTitle` or reuse `cardTitle`. (`HabitChainCard.kt:292`)
      Fixed in [#190](https://github.com/mandrecode/tempo/pull/190).
- [x] **G13 — Add-action parity.** Routines renders its own in-content 76dp FAB
      (end 20 / bottom 12dp, `surfaceContainerHighest`); tasks' add action lives in the floating
      bar. Verify both tabs expose a visually consistent add affordance at every tier, including
      single-tab mode. (`RoutinesContent.kt:212-239`, `core/ui/navigation/FloatingBarTaskActions.kt`)
      Investigated in [#191](https://github.com/mandrecode/tempo/pull/191): both tabs already
      share one add-action system via `PersistentFloatingBar`; documented the parity rather than
      changing behavior.
- [x] **G14 — `contentDescription = null` audit.** ~30 null descriptions across UI; confirm each
      is genuinely decorative (expand chevrons inside clickable pills are borderline). Fixed in
      [#192](https://github.com/mandrecode/tempo/pull/192): added missing container-level checkbox
      semantics to `TaskItem`/`HabitItem`; the rest were already correctly decorative.
- [x] **G15 — Muted-text alphas drift.** Completed title/description alphas: tasks 0.6/0.4 vs
      habits 0.5/0.35. Standardize the muted-content alpha ladder (e.g. 0.7/0.5/0.35) in one place.
      Fixed in [#193](https://github.com/mandrecode/tempo/pull/193): introduced
      `MutedContentAlpha.TITLE`/`DESCRIPTION` (0.6/0.4) shared by both card types.

---

## 5. Maintenance

- When a release pass finds a new class of regression, add one criterion (and delete a stale one).
- New semantic tokens go in `Type.kt` with a KDoc role description; new shared paddings in
  `Spacing.kt` once G6 is resolved.
- Keep gap IDs stable; close them with PR links rather than deleting entries until 1.0 ships.
