# AGENTS.md

Native Android app (Tempo & Habits) — single runtime `:app` module plus auxiliary tooling modules, Clean Architecture + MVI, Jetpack Compose, Navigation 3.

## Default OpenSpec Workflow

OpenSpec is the default workflow for substantive work. Agents should use it automatically; the user should not need to explicitly request OpenSpec.

Before starting substantive implementation, run `openspec list --json` to understand active changes and choose the appropriate OpenSpec action:

- If the user is exploring an idea, comparing approaches, clarifying requirements, or the request is ambiguous, use the `openspec-explore` skill.
- If the request is a new substantive feature, behavior change, non-trivial bug fix, architecture change, data/model change, or cross-layer refactor and no matching active change exists, use the `openspec-propose` skill before implementation.
- If a matching active change exists, or the user asks to implement/continue work that maps to an existing change, use the `openspec-apply-change` skill.
- If implementation is complete and the change is ready to finalize, suggest or use the `openspec-archive-change` skill as appropriate.

After completing a change's implementation and required verification, update its
task checklist and archive it immediately with `openspec-archive-change`. When a
change is delivered through a PR, include that archive move in the same PR before
its final verification run, so the final green CI validates the complete delivered
state. Do not leave completed changes active. If a change is deprecated,
superseded, or solved outside its recorded task list, archive it with that status
understood rather than keeping it active. Keep a change active when it is still
being pursued and implementation, verification, review follow-up, or required
device validation is pending; do not archive it solely because the app code
appears complete.

Do not use OpenSpec for trivial one-off work: pure Q&A, code review, running a simple command, formatting-only changes, tiny comments/docs wording, or narrowly scoped edits that do not change behavior or architecture. When in doubt, prefer OpenSpec for anything that affects product behavior, persistence, scheduling, notifications, navigation, or multiple layers.

## Layer-Specific Rules

Before writing code, read the reference doc for the layer you are touching:

| Layer | Doc | Non-obvious rules |
|:---|:---|:---|
| UI / Screens | [`docs/agents/UI_UX.md`](docs/agents/UI_UX.md) | Screen/Content split; `@Preview` in `src/debug/` (or `src/screenshotTest/`) only; `kotlinx.collections.immutable` for state |
| Domain / UseCases | [`docs/agents/DOMAIN.md`](docs/agents/DOMAIN.md) | Pure Kotlin — zero `android.*` imports; `kotlinx-datetime` only (no `java.time`) |
| Data / Room / Repos | [`docs/agents/DATA.md`](docs/agents/DATA.md) | Mapper extensions (`toDomain()`/`toEntity()`); bind domain/data repos in `core/di/RepositoryModule.kt`, `SharedPreferences`-backed repos in `core/di/PreferencesRepositoryModule.kt` |
| Tests | [`docs/agents/TESTING.md`](docs/agents/TESTING.md) | MockK (`relaxed = true`), Truth assertions, Turbine for Flows; screenshot tests only for adaptive-layout regressions |
| Tech stack / versions | [`docs/agents/TECH_STACK.md`](docs/agents/TECH_STACK.md) | Do not add libraries not listed here without asking |

## Commands

```bash
./gradlew assembleDebug          # Build
./gradlew testDebugUnitTest      # Unit tests (CI uses this, not `test`)
./gradlew koverVerifyDebug       # Coverage thresholds: 80% line, 70% branch
./gradlew validateDebugScreenshotTest  # Compose Preview screenshot tests (JVM, no emulator)
./gradlew updateDebugScreenshotTest    # Regenerate reference images after an intended UI change
./gradlew ktlintCheck            # Formatting lint
./gradlew ktlintFormat           # Auto-fix formatting — MUST run before every commit
./gradlew :app:detekt            # Static analysis (code smells, complexity, naming)
./gradlew kspDebugKotlin         # Regenerate Room schemas (exported to app/schemas/)
```

JDK 21 required. Version is read from `version.txt` at repo root.

## CI / Quality Gates

- **detekt and ktlint are blocking.** The `📐 Static Analysis` job in `.github/workflows/ci.yml` fails the build if `./gradlew ktlintCheck` or `./gradlew :app:detekt` fail. Run `./gradlew ktlintFormat` and `./gradlew :app:detekt` locally before pushing.
- **The detekt baseline is frozen at 155 and may only decrease.** `app/detekt-baseline.xml` is the suppression ceiling. A CI guard counts `<ID>` entries and fails if the count exceeds 155. Do **not** add new suppressions to grow the baseline — fix the issue instead.
- **Screenshot tests are blocking.** The `🖼️ Screenshot Tests` job runs `./gradlew validateDebugScreenshotTest` on every PR — JVM only, no emulator — and `✅ CI Status` requires it. If you change adaptive layout on purpose, run `./gradlew updateDebugScreenshotTest`, **review the diff report** at `app/build/reports/screenshotTest/preview/debug/index.html`, and commit the regenerated references. Never regenerate to make a red build green without looking at what moved.
- **When you fix detekt issues**, regenerate/shrink the baseline (e.g. `./gradlew :app:detektBaseline` then prune resolved entries) and lower the `MAX=155` ceiling in the CI guard accordingly so the gate ratchets down.

## Local Instrumented Tests

CI runs `connectedDebugAndroidTest` only on push to `main`. For local automated
instrumented checks, default to an AVD instead of a physical device so results are
reproducible. Create and reuse a Pixel 10 AVD for this purpose:

```bash
android emulator create --profile pixel_10 # one-time; creates a Pixel 10 AVD
android emulator list                       # find the AVD name
android emulator start <avd-name>
./gradlew connectedDebugAndroidTest
android emulator stop <avd-name>
```

The created AVD uses the CLI's default API level, which may differ from CI (API 31). For exact CI parity, follow the `android-cli` skill documentation (in your agent skills registry) to set up an API 31 emulator; if that documentation is unavailable, use `android --help` and manually choose an API 31 system image.

For manual app smoke testing, prefer the user's real connected Pixel 7 when it is visible in `adb devices -l`; if no physical Pixel 7 is connected, fall back to the Pixel 10 AVD. Do not rely on the physical device as the default target for automated instrumented test checks.

## Architecture Constraints

- **Runtime module boundary:** production app code lives in `:app`. Auxiliary non-runtime modules (for example `:benchmark`) are allowed when they target or support `:app` and do not contain production domain, data, or UI implementation.
- **Domain layer** must be pure Kotlin. No `android.*` imports, no Room, no Compose types.
- **Data layer** is the only layer that touches Room, SharedPreferences, or network.
- **UI layer**: `*Screen.kt` owns ViewModel/navigation; `*Content.kt` is pure Compose with no ViewModel reference. All `@Preview` composables go under `src/debug/` — or `src/screenshotTest/` for the `@PreviewTest` previews backing committed reference images — never the main source set.
- **No hardcoded strings** in composables — use `stringResource()`.
- **Collections in UiState** must use `kotlinx.collections.immutable` types (`ImmutableList`, etc.).
- **Trailing commas** in multi-line declarations.
- **Settings feature scope (D3 decision):** Settings is a deliberate thin exception to full Clean Architecture layering. It may remain presentation/data-light without a dedicated `features/settings/domain/` use-case layer unless orchestration complexity grows (multi-repository rules, non-trivial workflows, or reusable business policies). If complexity increases, promote Settings to the standard domain/use-case structure.

## Localization

- Every translatable string resource (e.g. `<string>`, `<plurals>`, `<string-array>`) in `app/src/main/res/values/strings.xml` MUST have a matching entry in every locale-specific `app/src/main/res/values-<locale>/strings.xml` folder in the same change.
- Locale files must not contain orphan translations: each translated entry must have an `en` counterpart in `app/src/main/res/values/strings.xml`.
- Strings marked `translatable="false"` are exempt and need not appear in locale folders.
- CI runs `./gradlew lintDebug` and fails on `MissingTranslation` and `ExtraTranslation`. Run it locally before pushing if you touched `strings.xml`.

## New Feature Checklist

1. **Domain**: models in `features/[feat]/domain/model/`, repo interface in `domain/repository/`, use cases in `domain/usecase/`
2. **Data**: entity in `core/data/entity/`, DAO in `core/data/local/dao/`, mapper in `features/[feat]/data/mapper/`, repo impl in `data/repository/`, add `@Binds` in `core/di/RepositoryModule.kt` (domain/data repos) or `core/di/PreferencesRepositoryModule.kt` (`SharedPreferences`-backed repos)
3. **UI**: Contract (`UiState`/`UiEvent`/`UiEffect`), ViewModel, Screen, Content + Previews in `src/debug/`
4. **Tests**: unit test use cases and ViewModel; UI test Content composable

## Room Schema

CI verifies `app/schemas/` is up-to-date after `kspDebugKotlin`. If you change entities or migrations, regenerate schemas locally and commit the updated JSON files.

## Git Conventions

### Branches
- With issue: `<type>/[ID]-<slug>` (e.g. `feat/88-habit-history-graph`)
- Without issue: `<type>/<slug>` (e.g. `fix/crash-on-empty-list`)

### Commits
- With issue: `<type>(#[ID]): <description>`
- Without issue: `<type>: <description>`
- Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`

### Issue ID Resolution
Extract ID from branch name. If no ID in branch, look up with `gh`. If none found, omit the scope entirely — never invent IDs or use placeholder numbers.

### Pull Requests
- Title matches commit format above. Base branch: `main`.
- Body: summary of changes + `Closes #[ID]` if applicable.

### Copilot Review
- Don't request Copilot reviews on PRs: not through the reviewers API, not with `@Copilot` comment pings, and not by enabling the repository's Copilot review ruleset. This project doesn't use them.
- If a Copilot review already exists on a PR, a real finding in it is fixed like any other feedback; reply in its thread and resolve it. Never request another pass.

### Pre-Commit
Always run `./gradlew ktlintFormat` explicitly before committing. A git hook exists (`bash scripts/install-hooks.sh`) but do not rely on it.

### Workflow
Always work on a new branch — never commit directly to `main`. Create a PR when the work is done.

### OpenSpec change names
Same pattern as branches: `<type>-<id>-<slug>` when an issue exists, `<type>-<slug>` otherwise (e.g. `fix-691-habit-history-respects-periodicity`). Use the same `type` set as commits/branches. Never invent issue IDs.
