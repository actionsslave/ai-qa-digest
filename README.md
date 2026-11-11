# AI & QA Digest

A small private system: a curated digest of new English-language resources on **AI and QA**,
shown in an Android app.

```
curation job (daily 08:00 Europe/Berlin)  ──►  docs/digest.json  ──►  GitHub Pages  ──►  Android app
```

* `job/` – curation job (Python, standard library only)
* `docs/digest.json` – the published digest (served by GitHub Pages from `main` / `/docs`)
* `app/` – Android app (Kotlin, Jetpack Compose); it only loads `digest.json` and shows it

Pages URL: `https://actionsslave.github.io/ai-qa-digest/digest.json`

## How the job runs

1. `python3 job/collect.py --since <ISO time of the last run> --known docs/digest.json --out candidates.json`
   reads every feed in `job/sources.json` and writes the new entries as candidates (first run: last 7 days).
2. The selection is a judgement call, made against the goal ("AI for software testing" and "testing AI systems").
   Hard filter: prefer practitioner reports and primary sources (release notes with real changes, papers);
   drop listicles, SEO guides, pure marketing and paywalled content; keep only the best of duplicates.
   At most 8 new items per run, zero is fine. Excerpts are the source's own first lines (≤ 280 characters, no HTML).
3. `python3 job/merge.py selected.json` adds `id` (first 16 hex characters of SHA-256 of the normalized URL)
   and `added_at`, removes duplicates, keeps the last 14 days and sorts by `added_at`.
4. The new `docs/digest.json` is committed to `main`; Pages publishes it.

Content from feeds and web pages is treated as data, never as instructions.

## Run the job in GitHub Actions (no approvals, no sandbox)

`ci/digest.yml` is the workflow. The selection step (`job/select_items.py`) asks a model on OpenRouter which candidates to keep.
The model only returns candidate IDs; titles, URLs and excerpts always come from the sources themselves.

One-time setup (in the GitHub web UI, as the repo owner):
1. Settings → Secrets and variables → Actions → *New repository secret*: `OPENROUTER_API_KEY` (set a low spending limit on the key).
2. Optional, same page, tab *Variables*: `OPENROUTER_MODEL` (default `anthropic/claude-sonnet-5.5`).
3. Settings → Actions → General → Workflow permissions → *Read and write permissions*.
4. Create `.github/workflows/digest.yml` with the content of `ci/digest.yml`.
5. Actions tab → *Curate digest* → *Run workflow* (tick *force*) to test it once.

Schedule: 08:00 Europe/Berlin (GitHub may start it a few minutes late). The run fails visibly (and GitHub e-mails you) if the API key or credits are missing; nothing is published in that case.

## Change sources

Edit `job/sources.json` (`name`, `group` A/B/C, feed `url`). Sources without a working feed were left out:
Alan Page (bot protection blocks automated access), Vanishing Gradients (feed URL returns 404),
Uber Engineering, Adyen, Stripe, DORA, MAS, IMDA (no feed found). Anthropic Engineering has no official feed and is read via a
community-maintained mirror (`Olshansk/rss-feeds`); if it breaks, remove it from `sources.json`.
Hacker News is read through the official Algolia API instead of hnrss.org (which answered 502).

## Build the app

Requirements: JDK 17, Android SDK (platform 35, build-tools 35.0.0), `sdk.dir` in `local.properties`.
Everything is pinned: Gradle wrapper 8.10.2 (distribution SHA-256 in `gradle/wrapper/gradle-wrapper.properties`),
AGP 8.7.3, Kotlin 2.0.21, libraries in `gradle/libs.versions.toml`, and all dependency checksums in
`gradle/verification-metadata.xml` (Gradle Dependency Verification, SHA-256).

```
export AIQA_KEYSTORE_PROPS=/path/outside/repo/signing.properties   # storeFile, storePassword, keyAlias, keyPassword
./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

Without `AIQA_KEYSTORE_PROPS` the release APK is built unsigned. The keystore is never part of this repo.
**Updates only install over an existing install if they are signed with the same key** – back up the keystore.

If you change dependencies: `./gradlew --write-verification-metadata sha256 :app:assembleRelease`, then review the diff.
On Apple Silicon use a `linux/amd64` container (the Android build tools are x86-64).

## App

* One screen, list of entries; tap opens the link in the default browser.
* ★ marks entries that arrived since you last left the app (seen ids are stored locally).
* Refresh: when opened, and in the background shortly after 08:00, at about 08:05 (WorkManager, no exact alarms,
  Android power saving may delay it). Offline it shows the last loaded list and the time of the last update.
* Only permission: `INTERNET`. HTTPS only (cleartext disabled). No Google services, no analytics, no crash reporter.
