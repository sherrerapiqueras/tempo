# Design

## Decisions

### D1. One flow, two hosts
`DriveRestoreFlow` is a plain class, injected per screen, with its own small step state (`DriveRestoreStep`). It drives the UI through a `Host`: the scope, `showStep`, and `launchConsent`. Settings and onboarding each map the step into their own `UiState` and route `DriveRestoreEvent`s to it. `DriveRestoreDialogs` renders any step. That keeps the restore rules and copy in one place and keeps both ViewModels thin.

### D2. Restore goes through the normal import
`RestoreDriveBackupUseCase` downloads the file and calls `ImportBackupUseCase`. That means:
- decryption, schema-version checks and validation all happen before any write;
- the write is one transaction;
- reminders are rescheduled afterwards.

Every `ImportOutcome` maps onto a distinct `RestoreOutcome`, and download failures map onto the `DriveBackupFailure` cause.

### D3. Merge only when there is something to merge into
`BackupRepository.hasUserData()` is true for any task, habit or chain, or any non-default category. The seeded Inbox alone isn't user data, so a fresh install restores by **replace**, which keeps backup identities (v2). A device in use asks, and **merge** uses the identity-first planner from #283, so nothing is duplicated.

### D4. Keeping the passphrase only as long as needed
The typed passphrase is zeroed after the import. A copy is kept only while the "keep backing up" offer is on screen, and it's zeroed on any answer, dismissal or failure. Accepting hands copies to `EnableDriveBackupUseCase`, which zeroes them.

### D5. Newest backup only
The newest backup is what users expect, and retention (#306) keeps 5. Choosing an older one is out of scope; the list is already sorted, so a picker could be added later without changing the flow.

## Risks / Trade-offs

- **[Risk] No real Drive to test against yet.** → Unit tests cover the flow and use cases with fakes. The wiped-device end-to-end check waits for the user's OAuth client (`docs/implementation/google-drive-setup.md`), and that task stays open.
