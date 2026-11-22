# Tempo Documentation

An index of what is written down and where. Start at [`AGENTS.md`](../AGENTS.md) for conventions and
workflow; everything here is reference material.

## For agents and contributors

| Doc | Read it when |
|:--|:--|
| [`agents/TECH_STACK.md`](agents/TECH_STACK.md) | Checking a library, an SDK level, or where a package lives |
| [`agents/UI_UX.md`](agents/UI_UX.md) | Building a screen or composable |
| [`agents/DOMAIN.md`](agents/DOMAIN.md) | Writing use cases or repository interfaces |
| [`agents/DATA.md`](agents/DATA.md) | Touching Room, mappers, or repository implementations |
| [`agents/TESTING.md`](agents/TESTING.md) | Writing unit or Compose tests |

## Features

| Doc | Covers |
|:--|:--|
| [`features/focus.md`](features/focus.md) | The Focus tab: the day's agenda, Up next, and focus sessions |
| [`features/habit-chains.md`](features/habit-chains.md) | Grouping habits into routines |
| [`features/habit-chain-selector.md`](features/habit-chain-selector.md) | Picking which habits a chain runs |
| [`features/habit-chain-collapse.md`](features/habit-chain-collapse.md) | Expanding and collapsing a chain card |
| [`features/habit-chain-deletion-options.md`](features/habit-chain-deletion-options.md) | What deleting a chain does to its habits |
| [`features/habit-chain-live-activities.md`](features/habit-chain-live-activities.md) | The live notification that walks a chain |
| [`features/inverted-habits-chains-restriction.md`](features/inverted-habits-chains-restriction.md) | Why inverted habits cannot join a chain |
| [`features/habit-history.md`](features/habit-history.md) | The completion history visualization |
| [`features/theme-setting.md`](features/theme-setting.md) | Theme selection and Material You |

## Data & security

| Doc | Covers |
|:--|:--|
| [`BACKUP_FORMAT.md`](BACKUP_FORMAT.md) | Export/import schema, versioning, conflict handling, encryption |
| [`DB_ENCRYPTION.md`](DB_ENCRYPTION.md) | SQLCipher setup and the Keystore-held database key |
| [`implementation/MIGRATIONS.md`](implementation/MIGRATIONS.md) | Room migration practice |
| [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) | The published privacy policy. Settings > About links to this file on `main` (`PRIVACY_POLICY_URL` in `app/build.gradle.kts`), so moving or renaming it breaks the link in every installed build |

## Design & implementation notes

- [`design/`](design/) — visual design, typography, colour, and the release visual-consistency
  checklist
- [`implementation/`](implementation/) — notes left behind by past changes (notifications, fonts,
  timezone handling, MVI refactoring, day filtering, Play Store changelog)
- [`performance/startup-audit.md`](performance/startup-audit.md) — cold-start measurements and what
  was done about them

## Where the reasoning lives

Per-change proposals, designs and spec deltas are in
[`openspec/changes/`](../openspec/changes/) — including [`archive/`](../openspec/changes/archive/)
for completed ones. For anything shipped recently, that is the fuller and more current record; the
pages here summarise the parts worth keeping at hand.
