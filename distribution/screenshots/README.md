# Play Store Screenshots

Source of truth for how the screenshots under this directory were produced.
Not wired into any CI workflow — screenshots are uploaded to the Play
Console listing manually, per locale. Re-run the pipeline below whenever
the app's UI changes enough to make these stale.

## Layout

```
distribution/screenshots/
  phone/     phone_{en,es}_{light,dark}_{1_focus_today,2_focus_session,3_routines_today,4_tasks_personal}.png
  medium/    medium_...
  expanded/  expanded_...
  desktop/   desktop_...
  xr/        xr_...
  play-store/
    phone/ medium/ expanded/ desktop/ xr/
               {en,es}_{1_focus_today,2_focus_session,3_routines_today,4_tasks_personal,5_private}.png
```

4 views × light/dark × 2 locales × 5 form factors = 80 raw captures, and
5 designed listing images × 2 locales × 5 form factors = 50 under `play-store/`.
The raw captures are the input; the `play-store/` images are what gets uploaded
(see [Designed listing images](#designed-listing-images)).

The four views are one story told in order: the day as Focus sees it, a session
started on it, and then Routines and Tasks with that session still running.
Capturing them in that order is what puts the running session into the last two,
so the sequence in `capture-screenshot-set.py` is not arbitrary.

How the session shows there depends on how much room the navigation has:

| Form factor | Navigation | With a session running |
|---|---|---|
| Phone | bottom bar | Focus tab widens into a pill with the countdown |
| Medium tablet | compact icon-only rail | Focus tab tinted; no room for digits |
| Expanded tablet, Desktop | labeled rail | countdown replaces the "Focus" label |
| XR | compact icon-only rail | Focus tab tinted; no room for digits |

Two things about the Tasks shot are load-bearing:

- It opens the **Personal** category, the one seeded category with nothing
  completed in it. A category with completed tasks gains a clear-completed
  action, and the floating bar drops the countdown to make room
  (`hasContextualActions` in `PersistentFloatingBar.kt`) — taking the running
  session out of a shot that exists to show it running.
- `generate-seed-backup.py` orders Personal first among the user-made
  categories so its chip is on screen without scrolling on a phone, where the
  row only fits about three.

## Prerequisites

- `android` CLI, `adb`, `avdmanager` (Android SDK)
- `python3` with [cryptography](https://pypi.org/project/cryptography/) (used to
  encrypt the seed backup); for XR captures also
  [Pillow](https://pypi.org/project/Pillow/)
- Google Chrome, for rendering the designed listing images (headless; any
  recent version). Install the Python packages into a venv if the system
  blocks a global `pip install` — `python3 -m venv /tmp/venv &&
  /tmp/venv/bin/pip install cryptography Pillow`
- A debug build: `./gradlew assembleDebug`

Debug builds no longer carry a "(Debug)" suffix on `app_name`, so the build
needs no temporary edit before capturing: Desktop's window caption and the
expanded tablet's labeled rail header both read "Tempo" as they are.

## How seeding works (and why it's not SQL any more)

Until GitHub issue #212 the pipeline seeded data by pulling the app's
`databases/tempo_database`, applying INSERT statements with the **host's**
`sqlite3`, and pushing the file back. That is no longer possible: the Room DB
is now SQLCipher-encrypted at rest, and its passphrase is generated and
Keystore-wrapped on-device (`KeystoreDbPassphraseProvider`), so it never
leaves the app process. A pulled DB file is opaque ciphertext.

The **backup export/import** feature is the way in. Its files are encrypted with
a *separate*, caller-chosen passphrase (PBKDF2WithHmacSHA256 + AES-256-GCM,
specified in [`docs/BACKUP_FORMAT.md`](../../docs/BACKUP_FORMAT.md)), unrelated
to the DB's own key — so a script can build a backup file and let the app import
it exactly as a user would, writing through the app's own encrypted DB layer.

Note the seed file is named **`.json`, not `.tempo`**, even though `.tempo` is
what the app suggests when *exporting*. The import picker is launched with the
`application/json` MIME filter, so a `.tempo` file is not selectable in
DocumentsUI and the pipeline would stall waiting for a file it can never tap.

## The pipeline

Four scripts, composed by a fifth:

- **`scripts/generate-seed-backup.py --locale en|es --theme light|dark
  --passphrase P -o FILE`** — writes an encrypted backup (as `.json`, see above)
  containing a curated demo dataset (14 tasks across 4 categories with a mix of
  priorities/due dates/completion states, 7 habits split Build/Quit, one
  3-member habit chain), with category names, task titles/descriptions, and
  habit titles translated per locale. Every date is computed relative to
  `--today` (defaults to the real current date), so re-running later doesn't
  produce stale "Today" tasks or broken streaks. Focus lists only tasks due
  today plus overdue ones, so four tasks are dated today and one yesterday —
  enough to fill the hero Focus shot and show its Overdue section. It also emits a `settings`
  block carrying `--theme`, since Replace-mode imports apply that section and
  would otherwise overwrite the theme seeded into `theme_prefs.xml`.
- **`scripts/import-seed-backup.py <serial> <file-name> <passphrase> <en|es>`**
  — drives the real UI to import that file: Settings → Import data → system
  file picker (searched by name) → passphrase dialog → Merge/Replace
  (Replace) → success dialog. Retries the whole flow up to 3 times, since
  driving real UI is inherently flaky and Replace-mode import is idempotent.
- **`scripts/seed-screenshot-data.sh <serial> [light|dark] [en|es]`** —
  wipes the target device's app data, grants the notification/exact-alarm
  permissions the app would otherwise prompt for (screenshot capture
  skips onboarding), sets the theme, sets the **app's per-app language**
  (see below) to match, then generates and imports the backup via the two
  scripts above.
- **`scripts/capture-screenshot-set.py <serial> <out-dir> <prefix> <en|es>`**
  — navigates the seeded app (Focus/Today → start a session from the "Up
  next" card → Routines/Today → Tasks/Personal-category) and screenshots
  each via `adb screencap`. Navigation finds UI elements by `android
  layout`'s content-desc, falling back to plain text (desktop's nav rail
  only sets text, not content-desc); the target text/desc strings are
  looked up per locale in the script's `NAV` dict. The start button is
  matched on the *verb* of `focus_session_start` plus a digit, because the
  rest of its label is the session length from Settings.

  Current `android layout` releases return one entry per window, each with
  its nodes nested under `content`, upper-case `interactions` flags and
  package-qualified resource ids. Both scripts flatten that, normalise the
  flags and ids, and search **only the focused window** — on XR the dump
  carries every window in the scene (home environment, System UI, a stray
  Gboard settings screen), whose labels would otherwise match first. That
  parsing lives once, in `scripts/android_ui.py`, which both scripts import.

  The capture runs with **SystemUI demo mode** on (`set_demo_mode`): a 9:41
  clock, full battery and Wi-Fi, and no notification or work-profile icons,
  so every status bar in the set matches. It is switched off again when the
  run ends, even if a step fails.

  The Focus tab only answers to the content-desc "Focus" while nothing is
  running — once a session starts it relabels itself to the time left — so
  the first step relies on the `pm clear` that `seed-screenshot-data.sh`
  does. Running this script twice against the same device without
  re-seeding fails on step 1 rather than producing a wrong image.

  Every shot **waits for a piece of content only the finished screen has**
  (the start button, the "Focusing" status, the seeded chain title, the
  first seeded Personal task) before the shutter goes. A fixed settle alone
  was not enough on XR: Focus was still drawing its loading indicator, and
  the resulting blank panel with a spinner in the middle reads as a
  legitimately empty day rather than as a failed capture. If you add a
  view, give it a marker too — a fixed sleep will pass on a fast emulator
  and quietly produce a bad image on a slow one.
- **`scripts/generate-screenshot-set.sh <serial> <out-dir> <prefix> <en|es>`**
  — runs the above twice (light, then dark) for one form factor and
  locale, and crops XR output to 16:9 when `prefix` is `xr` (see below).

### Localization

Two independent things need to be in the target language, and the
pipeline handles both:

1. **The app's own UI strings** (nav labels, "Today"/"Yesterday", Settings
   screen, etc.) — controlled by the app's **per-app language** (Android
   13+ `LocaleManager`; the app declares `en-US`/`es-ES` support in
   `res/xml/locales_config.xml`). `seed-screenshot-data.sh` sets this via:
   ```bash
   adb shell cmd locale set-app-locales <pkg> --user 0 --locales es
   ```
   This only changes the *app's* language, not the whole emulator/system
   locale — simpler and doesn't require a reboot. Verify with
   `adb shell cmd locale get-app-locales <pkg> --user 0`.
2. **The seeded demo content** (category names, task/habit titles) —
   plain data written by `generate-seed-backup.py --locale`, translated by
   hand in that script's `LOCALES` dict. Add a new locale by adding an
   entry there (matching the app's actual `res/xml/locales_config.xml`
   support), plus entries in `NAV` in `capture-screenshot-set.py` and
   `STRINGS` in `import-seed-backup.py` with the corresponding translated
   strings (look them up in `app/src/main/res/values-<locale>/strings.xml` —
   e.g. `focus`, `routines`, `tasks`, `focus_session_start`,
   `backup_import_title` — don't guess; a mismatched string means the
   script can't find the element to tap).

   Note that the **system file picker** follows the emulator's *system*
   locale, not the app's per-app one, so `import-seed-backup.py` addresses
   its elements by resource id rather than by label.

## Devices

Each form factor uses a dedicated AVD matching a real device category —
**not** a single resizable AVD with `resize-display` presets. That was
tried first and abandoned: the app has a single width breakpoint at
1200dp (`SheetPlacement.kt`), and both the "medium" and "expanded"
`resize-display` presets landed above it regardless of orientation
(`resize-display` applies a 240dpi density override, e.g. 1920px at
240dpi = 1280dp), so medium and expanded screenshots looked identical.

| Form factor | AVD | Orientation | Result |
|---|---|---|---|
| Phone | `Pixel_10` | portrait (native) | 1080×2424 |
| Medium tablet | `Medium_Tablet` | **portrait** (forced) | 1600×2560, 800dp width — below the 1200dp breakpoint, compact icon-only rail |
| Expanded/big tablet | `Pixel_Tablet` | landscape (native) | 2560×1600, 1280dp width — above the breakpoint, full labeled rail with app title |
| Desktop | `Large_Desktop` | landscape (native) | 1920×1080, exact 16:9 |
| Android XR | `XR_Headset` | n/a (square passthrough scene) | 2558×2558 → cropped to 2558×1439 |

Create any that don't already exist:

```bash
android emulator list                     # check what's already there
android emulator create Medium_Tablet     # profile exists in --list-profiles
android emulator create Large_Desktop     # profile exists in --list-profiles
# Pixel_Tablet isn't in the simplified `android` CLI's profile list —
# create it directly via avdmanager, reusing whatever system image an
# existing AVD (e.g. Pixel_10) uses:
avdmanager create avd -n Pixel_Tablet \
  -d pixel_tablet \
  -k "system-images;android-37.1;google_apis_ps16k;arm64-v8a"
# Pixel_10 should already exist per AGENTS.md / android-cli conventions; if
# not, `android emulator create pixel_10`. The `android` CLI has no XR
# profile, so XR_Headset also goes through avdmanager (from
# `android sdk install cmdline-tools/latest`), after installing its image:
android sdk install "system-images/android-34/google-xr/arm64-v8a"
avdmanager create avd -n XR_Headset -d xr_headset_device \
  -k "system-images;android-34;google-xr;arm64-v8a"
```

### Emulator GPU mode

Launch every AVD with `-gpu host` (`emulator -avd <name> -gpu host`, or the
same flag through `android emulator start` if your version passes it on).
`android emulator start` 1.1 takes no GPU flag, so set it in each AVD instead:
`hw.gpu.mode=host` in `~/.android/avd/<name>.avd/config.ini` (new AVDs start
on `auto`).
Left to choose for itself, the emulator falls back to software rendering
(SwiftShader) for these large displays; on a loaded machine the 2560×1600
tablets then took 15–25 s to draw a frame, the keyboard hit ANRs, and the
import and capture steps timed out waiting for screens that hadn't drawn yet.
On host GPU the same cold start takes about 6 s.

That includes XR. Earlier emulator releases rendered icon glyphs as noise on
XR under `-gpu host` (nav rail, Settings rows, the window-chrome close button)
while text stayed fine; on emulator 37.2 the glyphs render correctly, and the
software renderer instead left System UI hung ("System UI isn't responding")
with the app never reaching the scene. Check the first XR capture's icons
before trusting a run on a different emulator version.

`Medium_Tablet` and `Pixel_Tablet` end up with the **same** physical spec
(2560×1600 @ 320dpi — apparently pinned by the Google Play tablet system
image regardless of requested device profile); orientation is what makes
them look different, not the hardware.

## Recreating the full set

Repeat each form factor's block once per locale (`en`, `es`) — swap the
`LOCALE=en` line and the `_en`/`_es` output naming is handled
automatically by `generate-screenshot-set.sh`.

```bash
./gradlew assembleDebug
APK=app/build/outputs/apk/debug/app-debug.apk

# --- Phone ---
android emulator start Pixel_10
adb -s emulator-5554 install -r "$APK"
adb -s emulator-5554 shell cmd overlay enable com.android.internal.systemui.navbar.gestural
for LOCALE in en es; do
  bash scripts/generate-screenshot-set.sh emulator-5554 distribution/screenshots/phone phone "$LOCALE"
done
adb -s emulator-5554 emu kill

# --- Medium tablet (portrait) ---
android emulator start Medium_Tablet
adb -s emulator-5554 install -r "$APK"
adb -s emulator-5554 shell cmd overlay enable com.android.internal.systemui.navbar.gestural
adb -s emulator-5554 shell settings put system accelerometer_rotation 0
adb -s emulator-5554 shell settings put system user_rotation 1   # 1 = portrait for this panel's native-landscape shape
sleep 2
# Verify with dumpsys, NOT `wm size` — `wm size` always reports the
# unrotated physical panel size, not the current logical/rotated size:
adb -s emulator-5554 shell dumpsys window displays | grep -o "cur=[0-9x]*"
for LOCALE in en es; do
  bash scripts/generate-screenshot-set.sh emulator-5554 distribution/screenshots/medium medium "$LOCALE"
done
adb -s emulator-5554 emu kill

# --- Expanded/big tablet (landscape, native — no rotation needed) ---
android emulator start Pixel_Tablet
adb -s emulator-5554 install -r "$APK"
adb -s emulator-5554 shell cmd overlay enable com.android.internal.systemui.navbar.gestural
for LOCALE in en es; do
  bash scripts/generate-screenshot-set.sh emulator-5554 distribution/screenshots/expanded expanded "$LOCALE"
done
adb -s emulator-5554 emu kill

# --- Desktop ---
android emulator start Large_Desktop
adb -s emulator-5554 install -r "$APK"
adb -s emulator-5554 shell cmd overlay enable com.android.internal.systemui.navbar.gestural
# The app may launch into a small floating window rather than maximized.
# Force-stopping and relaunching does NOT reliably fix it — the desktop
# shell remembers the window size. Launch the app, tap the maximize button
# in its window chrome (the middle of the three controls, top right), and
# verify with a screencap before capturing. That state then survives the
# `pm clear` + relaunch cycles the seeding does, so it only needs doing once.
adb -s emulator-5554 shell am start -n com.mandrecode.tempo.debug/com.mandrecode.tempo.MainActivity
for LOCALE in en es; do
  bash scripts/generate-screenshot-set.sh emulator-5554 distribution/screenshots/desktop desktop "$LOCALE"
done
adb -s emulator-5554 emu kill

# --- Android XR ---
# See "Emulator GPU mode" above: host GPU, and check the icons on the first
# capture. XR is still the slowest by far — expect one locale to take longer
# than a 10-minute timeout, so run each theme separately if you're driving this
# from a script with a deadline. Once a session is running its countdown keeps
# the UI busy, and `android layout` can fail repeatedly with "Could not obtain
# idle state"; the capture script retries, and for the session sheet (whose
# progress ring never stops) falls back to a fixed settle. A theme may still
# need a re-run.
android emulator start XR_Headset
adb -s emulator-5554 install -r "$APK"
adb -s emulator-5554 shell cmd overlay enable com.android.internal.systemui.navbar.gestural
for LOCALE in en es; do
  bash scripts/generate-screenshot-set.sh emulator-5554 distribution/screenshots/xr xr "$LOCALE"   # auto-crops to 16:9
done
adb -s emulator-5554 emu kill
```

If the auto-crop step fails with `ModuleNotFoundError: No module named
'PIL'` (system `python3` instead of a Pillow-equipped venv ran it), the
capture itself already succeeded — just re-run the crop manually:
`/path/to/venv/bin/python3 scripts/crop-xr-screenshots.py distribution/screenshots/xr`.
It's safe to call repeatedly; already-cropped files (height already
1439px) are skipped.

Check `adb devices -l` for the actual serial if more than one emulator
ends up running at once — `emulator-5554` is simply the first slot, not
guaranteed.

Then curate: review the resulting 8 images per form factor per locale,
replace sample data as needed by editing `generate-seed-backup.py`, and pick
the best up to 8 per Play Console's per-listing limit (currently
splitting evenly between light and dark).

## Designed listing images

`scripts/compose-store-screenshots.py` turns the raw captures into the images
that actually go on the listing (GitHub issue #181): a headline, a supporting
line and two feature chips around the capture, framed in a device. Run it after
any re-capture:

```bash
python3 scripts/compose-store-screenshots.py                    # everything
python3 scripts/compose-store-screenshots.py --only phone --locale es
python3 scripts/compose-store-screenshots.py --keep-html /tmp/pages   # keep the HTML to tweak in a browser
```

Each image is an HTML page rendered by headless Chrome (`--chrome PATH` if it
isn't at the default macOS location) at the exact size Play wants, using the
app's own Google Sans Flex from `res/font/` dialed as `Type.kt` dials it (round,
wide, heavy for headlines) and the Tempo palette from `Color.kt`.

| Form factor | Canvas | Frame |
|---|---|---|
| Phone | 1080×1920 (9:16) | phone, chips over its lower half |
| Medium tablet | 1440×2560 (9:16) | tablet, portrait |
| Expanded tablet | 2560×1440 (16:9) | tablet, text column on the left |
| Desktop | 2560×1440 (16:9) | monitor on a stand |
| XR | 2560×1440 (16:9) | the 2558×1439 crop, bezel-less with a glow, like a panel in space |

The five images tell the raw set's story and add a closer:

1. **Focus** — today and overdue tasks, with Up next (light)
2. **Focus sessions** — a running session (dark)
3. **Routines** — habit chains (light)
4. **Tasks** — the Personal category (dark)
5. **Privacy** — Focus in light and dark side by side, with the privacy chips

Light and dark alternate so the carousel shows both themes without repeating a
screen.

The copy lives in the script's `COPY` list: one entry per slide, English and
Spanish side by side, and `check_copy()` refuses to render if the locales stop
matching in shape (fields, chip count, one accented phrase per headline). Three
rules keep it honest and in the app's voice:

- **Claims match the capture and the app.** Focus lists tasks only (due today
  and overdue), and the session length is a setting, so the copy says neither
  "habits in Focus" nor "25-minute sessions". A chip that names something the
  screen doesn't show reads as a false claim.
- **The app's words, not new ones.** Headlines reuse onboarding lines where one
  fits ("Today, not everything" / "Hoy, no todo", "Work in sessions" /
  "Trabaja por sesiones"), and Spanish uses the terms `values-es` uses —
  Enfoque, atrasado, Completar/Pausar/Parar, Construir/Dejar, rachas — in
  Spain's Spanish, informal *tú*.
- **Listing text tells the same story.** `distribution/listing/` (title, short
  and full description, both locales) uses the same terms and claims; change
  them together.

## Play Store technical requirements (as of writing)

- **Phone / tablet**: no fixed aspect-ratio requirement observed to fail
  here; captured at each device's native resolution.
- **Desktop (Chromebook) listing**: 4–8 PNG/JPEG, ≤8MB each, 16:9 or 9:16,
  each side 1080–7680px. `Small_Desktop` (1366×768) fails on the
  768px height; `Large_Desktop` (1920×1080) is exact 16:9 and clears the
  minimum comfortably.
- **Android XR listing**: 4–8 PNG/JPEG, ≤15MB each, 16:9 or 9:16, each
  side 720–7680px.

## Android XR: why the crop step exists

`XR_Headset`'s `adb screencap` always captures the full square (2558×2558)
passthrough environment scene, regardless of `adb shell wm size`
overrides (confirmed: overriding size changes nothing about the actual
captured pixels — the override only affects the app's own logical
window, not the compositor's scene render). Square fails the 16:9/9:16
requirement outright, so `generate-screenshot-set.sh` calls
`scripts/crop-xr-screenshots.py` automatically whenever the form-factor
prefix is `xr`.

The crop window (`y=620` to `y=620+1439=2059` at full width, giving a
2558×1439 frame — the closest integer-height 16:9 crop at that width,
since 2558 isn't evenly divisible by 16) was derived by pixel-sampling a vertical
line through the floating app window across all four canonical views to
find its true on-screen bounds — the window-chrome pill top sits at
`y≈658` and the tallest panel (Focus/Today, which has the most content —
it was Settings before the views changed in GitHub issue #318, and both
land in the same place) bottoms out at `y≈2022` — then centering the crop
on that combined range
with roughly even margins. A naive default *center-of-canvas* crop
looked unbalanced (lots of empty sky above, content nearly touching the
bottom edge) because the floating window itself isn't centered in the
square scene. If the app's UI changes enough to shift where the longest
screen's content ends, re-derive `CROP_TOP` in `crop-xr-screenshots.py`
the same way:

```bash
python3 -c "
from PIL import Image
im = Image.open('distribution/screenshots/xr/xr_en_light_1_focus_today.png').convert('RGB')
x = 1000  # a column safely inside the panel, left of the chrome pill
prev = None
for y in range(0, im.size[1], 4):
    px = im.getpixel((x, y))
    if prev is None or sum(abs(a-b) for a,b in zip(px, prev)) > 30:
        print(y, px)
    prev = px
"
```
Look for the panel's light/dark background color persisting until it
jumps to the reddish passthrough-terrain color — that transition point
is the panel's true bottom edge.
