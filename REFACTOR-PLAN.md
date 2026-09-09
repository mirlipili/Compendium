# REFACTOR-PLAN.md

Ordered work items. One branch per item. Do not start the next item until the
previous is merged and verified on a device.

Context for every item lives in `CLAUDE.md` (Known Issues + Invariants). Read it first.

---

## 1. Version-gated updates — `feature/version-gated-updates`

**Problem:** every refresh downloads ~1.1 MB unconditionally; the existing version-check
code is never called; the app cannot tell the user how old its data is.

**Do:**

- Consolidate networking into a single Retrofit instance with an OkHttp client
  (shared by whichever repository survives). Add an OkHttp `Cache` so `ETag` /
  `If-None-Match` conditional requests work against the nginx-served static files.
- Fold the working logic from `NetworkRepository` into the live refresh path, or wire
  `MedicationRepository` to call it. Delete whichever class ends up redundant —
  do not leave two implementations.
- Refresh sequence must be:
  1. `GET /version.json`
  2. compare with stored `dataVersion` for the current language
  3. if equal → update `lastCheckedAt` only, download nothing, report "already up to date"
  4. if newer → download language JSON, parse fully, validate, replace transactionally,
     then persist `dataVersion` + `dataPublishedAt` + `lastCheckedAt`
- Validation before replace: parsed list non-empty AND `newCount >= currentCount * 0.8`
  (skip the ratio check when `currentCount == 0`). Reject and keep existing data otherwise,
  with a clear error message.
- Wrap `deleteAll()` + `insertAll()` in a Room `@Transaction` method on the DAO so a
  mid-insert failure rolls back.
- Expose `dataVersion`, `dataPublishedAt`, `lastCheckedAt` as StateFlow from the ViewModel
  and show them in the UI (list screen header or an overflow "Data status" item). Include a
  visible warning when `lastCheckedAt` is older than ~30 days.
- Delete `data/model/Version.kt` and `data/model/VersionInfo.kt` (dead duplicates).
- Validate the parsed `VersionInfo` explicitly before use rather than trusting Gson's
  handling of non-null Kotlin fields.

**Acceptance:** refreshing twice in a row performs exactly one full download; the second
reports "up to date" and transfers only `version.json`. Data age is visible in the UI.
`./gradlew assembleDebug` passes.

---

## 2. Offline language switching — `feature/offline-language-switch`

**Problem:** switching FR↔NL requires network and wipes the table, despite both datasets
shipping in `assets/`.

**Do:**

- Add a `language` column to the `medications` entity and make the primary key
  `(id, language)` — or use a composite key / separate tables, your call, but both
  languages must be able to coexist in the database.
- Write an explicit Room `Migration` (v3→v4). Do not use `fallbackToDestructiveMigration`;
  user notes must survive.
- All DAO queries filter by the active language.
- `switchLanguage()` must not touch the network. If the target language is absent from the
  database, populate it from bundled assets; only fetch from the server if assets are
  missing too.
- On first launch, populate both languages from assets.
- Fix `LanguageManager.detectAndSetDefaultLanguage()` — check `prefs.contains(KEY_LANGUAGE)`
  rather than testing the defaulted return value, so system-locale detection actually runs.
- Verify per-medication notes still resolve correctly if medication IDs are shared across
  languages (they are — same node IDs), so a note written in FR should be visible in NL.
  Confirm this is the desired behaviour before changing the notes schema.

**Acceptance:** airplane mode on, switch FR→NL→FR successfully, notes intact.

---

## 3. Ranked, diacritic-insensitive, fuzzy search — `feature/better-search`

**Problem:** unranked `LIKE '%q%'` across four columns; accent-sensitive; no typo tolerance.

**Do:**

- Add a normalized search column populated at insert time: lowercase, NFD-normalized,
  combining marks stripped (`java.text.Normalizer` + regex), punctuation collapsed.
  Normalize the query the same way. `Métacam` / `metacam` / `METACAM®` must all match.
- Rank results in tiers rather than one flat score:

  | Tier | Match | Score |
  |------|-------|-------|
  | 1 | exact (normalized) name | 100 |
  | 2 | name starts-with | 90 |
  | 3 | name contains | 75 |
  | 4 | any name token starts-with | 65 |
  | 5 | fuzzy: Jaro-Winkler ≥ 0.85 | 40–60 scaled |

  Sort by tier, then alphabetically. Prefer Jaro-Winkler over raw Levenshtein: it weights
  shared prefixes, which matches how drug-name typos actually behave.
- Search `composition` (active substance / INN) as a lower tier than `name`, so searching
  a molecule works but does not drown exact brand matches. This matters clinically — vets
  often recall the molecule, not the brand.
- Render fuzzy-tier results below a visible "Similar results" divider so approximate matches
  are never mistaken for exact ones.
- Dataset is ~1,570 records × 2 languages. In-memory ranking over a normalized column is
  fine; Room FTS4 is optional. Do not add a heavyweight search dependency.
- Debounce query input (~200 ms) in the ViewModel.

**Acceptance:** `metacam`, `Métacam`, `metcam` (typo) and `meloxicam` (INN) all surface the
right product, with exact matches first. No perceptible lag on a mid-range phone.

---

## 4. Background update checks — `feature/update-worker`

**Problem:** `UpdateWorker` is a stub and is never scheduled.

**Do:**

- Implement `doWork()` to run the version check from item 1 (check only, or check-and-download
  on unmetered networks — decide and document).
- Enqueue a `PeriodicWorkRequest` (daily, `NetworkType.CONNECTED`, battery-not-low) from
  `MainActivity` or an `Application` subclass, using `ExistingPeriodicWorkPolicy.KEEP`.
- Never auto-replace data that fails validation. On failure, leave existing data untouched
  and surface the state on next app open — no notifications.
- Update `lastCheckedAt` even when no new version exists, so the staleness indicator
  distinguishes "pipeline broken" from "no new medications approved".

**Acceptance:** worker runs on schedule, updates timestamps, never leaves the DB in a
worse state than before.
