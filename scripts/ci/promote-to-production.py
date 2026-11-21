#!/usr/bin/env python3
"""Promote the open-testing (beta) release with a given version code to production.

Used by .github/workflows/publish.yml after the production environment is approved. It moves the
exact build that open testers have been running, with its release notes, instead of building or
uploading again. The release goes to 100% of users.

Usage: promote-to-production.py <package-name> <version-code>
Env:   PLAY_STORE_SERVICE_ACCOUNT_JSON  service account key with access to the app in Play Console.
Needs: google-auth and requests (pip install google-auth requests).
"""

import json
import os
import sys

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
SOURCE_TRACK = "beta"  # Play's API name for open testing.
TARGET_TRACK = "production"


def fail(message: str) -> None:
    print(f"::error::{message}")
    sys.exit(1)


def check(response, action: str):
    if not response.ok:
        fail(f"{action} failed ({response.status_code}): {response.text}")
    return response.json() if response.content else {}


def main() -> None:
    if len(sys.argv) != 3:
        fail("usage: promote-to-production.py <package-name> <version-code>")
    package, version_code = sys.argv[1], sys.argv[2]
    key = os.environ.get("PLAY_STORE_SERVICE_ACCOUNT_JSON")
    if not key:
        fail("PLAY_STORE_SERVICE_ACCOUNT_JSON is not set.")

    credentials = service_account.Credentials.from_service_account_info(json.loads(key), scopes=[SCOPE])
    session = AuthorizedSession(credentials)
    app = f"{API}/{package}"

    edit_id = check(session.post(f"{app}/edits"), "Creating the edit")["id"]
    edit = f"{app}/edits/{edit_id}"
    try:
        promote(session, edit, version_code)
    except SystemExit:
        # Don't leave a half-made edit open in Play Console.
        session.delete(edit)
        raise
    print(f"Promoted version code {version_code} from open testing to production (100%).")


def promote(session, edit: str, version_code: str) -> None:
    source = check(session.get(f"{edit}/tracks/{SOURCE_TRACK}"), "Reading the open-testing track")
    release = next(
        (r for r in source.get("releases", []) if version_code in r.get("versionCodes", [])),
        None,
    )
    if release is None:
        fail(
            f"Version code {version_code} is not in open testing. A newer release may have replaced "
            "it there; approve that release's production job instead.",
        )

    promoted = {
        "versionCodes": [version_code],
        "status": "completed",
    }
    for field in ("name", "releaseNotes"):
        if field in release:
            promoted[field] = release[field]

    check(
        session.put(f"{edit}/tracks/{TARGET_TRACK}", json={"track": TARGET_TRACK, "releases": [promoted]}),
        "Updating the production track",
    )
    check(session.post(f"{edit}:commit"), "Committing the edit")


if __name__ == "__main__":
    main()
