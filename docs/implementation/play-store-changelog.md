# Play Store Changelog

Automated generation of user-friendly "What's New" text for Google Play Store
listings, based on the technical `CHANGELOG.md` maintained by release-please.

## How It Works

```
release-please (CHANGELOG.md) → generate-whatsnew.sh → build/whatsnew/
```

1. **release-please** creates a technical changelog in `CHANGELOG.md` from
   conventional commits.
2. **`scripts/generate-whatsnew.sh`** extracts the latest version's entries,
   strips technical details (commit hashes, PR links, issue references, scope
   prefixes). It also strips direct GitHub issue/PR URLs (including plain
   URLs and markdown links), then groups entries under user-friendly section
   headers:
   - `### Features` → `✨ New`
   - `### Bug Fixes` → `🐛 Fixes`
   - `### Performance Improvements` → `⚡ Performance`
   - `### ⚠ BREAKING CHANGES` → `⚠️ Breaking`

   If release-please lists the same cleaned entry under `BREAKING CHANGES`
   and any other section, the generated Play Store text keeps the breaking
   entry and omits the duplicate from non-breaking sections.

   Output is written to `build/whatsnew/whatsnew-en-US` and
   `build/whatsnew/whatsnew-es-ES` (Spanish headers over the same entries).
   It is generated at publish time and never committed.
3. **`publish.yml`** hands `build/whatsnew/` from its build job to the
   open-testing upload (`r0adkll/upload-google-play`, `whatsNewDirectory`), so
   release notes are uploaded alongside the AAB. Promotion to production reuses
   those same notes ([play-release.md](play-release.md)). The publish workflow
   also fails if generated changelog files still contain GitHub issue/PR links.

## Output

```
build/
└── whatsnew/
    ├── whatsnew-en-US    # English
    └── whatsnew-es-ES    # Spanish headers, English entries
```

The script truncates each file to stay under **500 characters** (Play Store
limit).

## Usage

### Automatic (CI/CD)

`publish.yml` runs the script for the release's version before uploading to
Google Play. No manual action is needed.

### Manual (Local)

```bash
# Generate for the latest version in CHANGELOG.md
./scripts/generate-whatsnew.sh

# Generate for a specific version
./scripts/generate-whatsnew.sh 0.1.7
```

Running it locally only previews the text in `build/whatsnew/`; publish
regenerates it from the tagged `CHANGELOG.md`.

## Adding a New Language

1. Add an output file and section labels for the locale (e.g.
   `whatsnew-fr-FR`) to `scripts/generate-whatsnew.sh`.
2. The `upload-google-play` action picks up every `whatsnew-<locale>` file in
   `build/whatsnew/` automatically.

## Customizing Release Notes

The auto-generated text is a good starting point but may need editing for
user-friendliness. Common adjustments:

- **Reword technical descriptions** into benefit-oriented language
  (e.g., "optimize task filtering" → "Tasks load faster")
- **Remove internal changes** that don't affect users
  (e.g., dependency updates, code style fixes)
- **Highlight key features** by moving them to the top of the list

The text comes from commit subjects, so the place to improve it is the
conventional commit subject (PR title) itself.

## Future Automation Ideas

When Play Store publishing is fully automated, consider:

- **AI-powered translation**: Use a translation API in CI to auto-generate
  `whatsnew-es-ES` from the English text.
- **AI rewriting**: Use an LLM step in CI to rewrite technical entries into
  user-friendly language automatically.
- **PR comment preview**: Add a workflow that posts the generated What's New
  text as a PR comment for review before release.
