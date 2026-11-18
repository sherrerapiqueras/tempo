# Tasks

## 1. Domain

- [x] 1.1 Add models, the store, preferences, cloud, authorization and scheduler interfaces, and the enable, disable, change-passphrase and run use cases. Verify with unit tests for each use case covering success, each failure cause, retention, and disable clearing the passphrase.

## 2. Infrastructure

- [x] 2.1 Add the Keystore passphrase store and backup/data-extraction exclusions. Verify with an instrumented round-trip test.
- [x] 2.2 Add the Drive REST client over an `HttpTransport` interface. Verify with unit tests for request shape, parsing and error mapping using a fake transport.
- [x] 2.3 Add `GoogleDriveAuthorization`, the worker, the scheduler, the notifier and DI bindings. Verify that it compiles, plus a unit test of the scheduler's constraints (`DriveBackupConstraintsTest`).

## 3. Settings UI

- [x] 3.1 Add the delegate, contract and section: status line, enable/consent/passphrase setup, frequency, Wi-Fi only, back up now, change passphrase, and turn off. Verify with delegate unit tests and a Compose UI test of the section's states.
- [x] 3.2 Add strings in English and Spanish. Verify that `lintDebug` passes translation parity.

## 4. Docs and integration

- [x] 4.1 Update `sync-boundary.md` (new prefs and secret file) and the README/backup docs. Verify by review.
- [x] 4.2 Run ktlint, detekt, unit tests, kover, `lintDebug`, the instrumented suite and `openspec validate --strict`. Verify: green.
- [x] 4.3 Run a real-Drive end-to-end backup once the OAuth client exists. Verified on a Pixel 7 with the Google Cloud OAuth client: consent, passphrase setup and backups in Drive's app folder.

## Workflow follow-up

- Archive in the same PR once 4.3 is done, or keep the change active if 4.3 is still pending at merge.
