# Google Play Release Pipeline

`.github/workflows/publish.yml` runs when release-please creates a GitHub release (or by hand with
a tag). Every release goes to **open testing** automatically, then waits for an approval before the
**same build** is promoted to **production**.

```text
release created ─▶ 📦 Build ─▶ 🧪 Open testing ─▶ (approval) ─▶ 🚀 Production
                   AAB, APK,    track "beta",       reviewer on    same version code,
                   changelog    100% of testers     play-production 100% of users
```

| Job | Environment | What it does |
|:---|:---|:---|
| 📦 Build | none | Builds the signed AAB and APK from the tag, attaches the APK to the GitHub release, generates the Play changelog, and uploads all three as artifacts. |
| 🧪 Open testing | `play-open-testing` | Uploads the AAB with its changelog to the open-testing track (`beta` in the Play API). |
| 🚀 Production | `play-production` | Waits for a required reviewer. On approval, runs `scripts/ci/promote-to-production.py`, which copies that version code and its release notes from open testing to production at 100%. Nothing is rebuilt or re-uploaded. |

## Approving a release

Open the *Publish* run for the tag (Actions → Publish, or the link GitHub sends to reviewers) and
choose **Review deployments → play-production → Approve**. Reject it to leave the release in open
testing only.

If a newer release reaches open testing while an older one is still waiting, GitHub cancels the
older pending production job: only the newest build can be promoted. Promoting a build that open
testing no longer has fails with an explicit error and changes nothing.

## One-time setup

1. **Play Console:** production access granted, and the open-testing track set up (countries and a
   tester list or open sign-up). The service account in `PLAY_STORE_SERVICE_ACCOUNT_JSON` needs
   permission to release to both open testing and production.
2. **GitHub environments** (Settings → Environments):
   - `play-open-testing`: no reviewers. Optionally restrict deployments to tags `v*`.
   - `play-production`: **Required reviewers** set to the maintainer. Optionally restrict to tags
     `v*` too.
   The previous `google-play` environment is no longer used and can be deleted.
3. **Google Drive backup:** the Google Cloud project's OAuth publishing status must be
   **In production** before the first production release; see
   [google-drive-setup.md](google-drive-setup.md).

The secrets (`KEYSTORE_*`, `KEY_*`, `PLAY_STORE_SERVICE_ACCOUNT_JSON`) stay repository secrets; no
environment-level secrets are needed.
