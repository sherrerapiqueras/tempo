# Backup Destinations Beyond Google Drive — Spike

Outcome of #308 (part of #12). **Documentation only**: findings come from public sources, checked
2026-11-17. Testing Proton Drive's Android provider on a device is still to be done (see
[Still open](#still-open)).

## Recommendation

1. **No direct Proton Drive integration now.** Proton has no supported third-party API yet (below).
   Revisit when Proton announces a supported public SDK release.
2. **A generic "backup folder" destination through Android's Storage Access Framework (SAF)** is the
   right long-term answer for non-Google storage. It's provider-agnostic, it fits Tempo's local-first
   stance, and it needs no provider-specific code. But it should **not** land in 1.1 or 2.0: its
   unattended reliability is unproven, and it depends on each provider. Track it as its own issue
   for after 2.0, starting with the reliability tests listed below.
3. **Until then, manual export already covers non-Google storage:** the system file picker lets the
   user save the encrypted `.tempo` file to any provider they have.

## Proton Drive: is there a usable API?

**Not yet, for third parties.**

- Proton publishes an open-source Drive SDK ([ProtonDriveApps/sdk](https://github.com/ProtonDriveApps/sdk)).
  Its README says it's used by Proton's own clients and is **not yet ready for third-party production
  use**.
- Proton's [January 2026 SDK update](https://proton.me/blog/drive-sdk-january-2026) says authentication
  and other modules needed for standalone third-party integrations aren't supported yet. An app would
  have to provide login, session management and address handling itself.
- The repository mentions a new cryptographic model targeted for late 2026 or early 2027, which will
  be a breaking change.
- Proton recommends integrating through the SDK rather than calling the Drive API directly, and says
  non-compliant clients may be rate-limited or blocked.
- Proton has said a broader third-party release is coming ([SDK preview](https://proton.me/blog/proton-drive-sdk-preview),
  [June 2026 update](https://proton.me/blog/drive-sdk-june-2026)), but no supported release was found
  as of this spike.

Building on an unsupported SDK, ahead of a planned breaking change and with its own auth to
maintain, isn't viable for a solo-maintained app's backup path.

## Proton Drive through SAF

**Unconfirmed, and likely insufficient today.**

- Public user reports ([Proton feedback thread](https://protonmail.uservoice.com/forums/932839-proton-drive/suggestions/46393474-proton-drive-integration-with-android-default-stor))
  say Proton Drive appears in the Files app and when an app opens a single file. It reportedly does
  **not** appear in the folder picker (`ACTION_OPEN_DOCUMENT_TREE`), which a backup destination
  needs.
- The same thread quotes Proton Support saying the Files-app integration works only while the Proton
  Drive app can run in the background. That is a direct risk for an unattended write.
- A user reports that App Lock in Proton Drive blocks access through the Files app.

These are user reports, not Proton documentation, and haven't been checked on a device.

## SAF as a general backup destination

How it would work: the user picks a folder once (`ACTION_OPEN_DOCUMENT_TREE`), Tempo calls
`takePersistableUriPermission`, and the backup worker writes the encrypted file into that folder
through `ContentResolver` on schedule.

**Strengths**
- Provider-agnostic: anything exposing a `DocumentsProvider` with tree support works without
  provider-specific code. That includes local storage, SD cards, and cloud apps that implement tree
  access.
- The user chooses where their data goes, and Tempo doesn't need to know.
- The persistable permission survives restarts.

**Risks, all to be measured before committing to it**
- **Background writes run inside the provider app.** Cloud-backed providers may defer the upload,
  need their app alive, or fail while a lock screen or app lock is on. The write can "succeed"
  locally while nothing reaches the cloud.
- **Permission loss:** the user (or a provider update) can revoke the grant, or the folder can be
  deleted or moved. The worker has to detect this and report it as an actionable failure, the same
  way Drive authorisation loss is handled.
- **No reliable "is it really uploaded?" signal** through SAF, so the status line couldn't honestly
  promise the data is off the device. This is the biggest gap against the #306 standard of an honest
  status.
- **Retention and listing** depend on the provider's support for `listFiles` and delete within a
  tree, which varies.

**Coverage:** a SAF destination would cover any provider whose Android app implements
`DocumentsProvider` with document-tree support. Which consumer cloud apps (Proton Drive, Nextcloud,
Dropbox, OneDrive) do so reliably, and whether their background writes actually upload, has to be
confirmed per provider. It isn't established here.

## Still open

Required by #308's acceptance criteria, and needing a device and the relevant accounts:

1. **Proton Drive on a device:** install Proton Drive, sign in, and check whether it appears in
   `ACTION_OPEN_DOCUMENT_TREE`. If it does, write a file from a background worker with the screen
   locked and confirm it reaches Proton Drive on another device.
2. **The same unattended-write test** for the other providers worth covering (Nextcloud, Dropbox,
   OneDrive, local storage).
3. **Permission revocation and folder deletion:** confirm the worker can tell these apart from
   transient failures.

A generic SAF destination should be scoped only after (1)–(3) are measured.
