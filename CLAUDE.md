# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

Offline Android reference for the Belgian veterinary drug registry (VetCompendium),
used by a practising vet in the field, often without network. **Offline correctness and
data integrity outrank features.** A wrong or empty drug list during a consult is a
clinical safety problem, not a cosmetic bug.

## Build Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease

# Tests
./gradlew test                    # Unit tests
./gradlew connectedAndroidTest    # Instrumented tests (device required)

# Single test class
./gradlew test --tests "vet.derichs.compendium.ExampleUnitTest"

# Clean build
./gradlew clean assembleDebug
```

## Architecture

MVVM with Jetpack Compose and Kotlin Coroutines/Flows. Single-activity, no fragments.

**Data flow:** `Assets/API → Repository → Room (DAO) → ViewModel (StateFlow) → Composable screens`

### Key layers

- **`data/model/`** — Room entities: `Medication`, `MedicationNote` (per-medication), `GeneralNote` (single global, id=1)
- **`data/database/`** — Room database **v5** with DAOs returning `Flow<>` for reactivity
- **`data/repository/`** — `MedicationRepository` (offline-first: assets → Room, optional remote refresh) and `NotesRepository`
- **`data/network/`** — Retrofit + OkHttp singleton (`MedicationApiService.getInstance(cacheDir)`) hitting `https://medicament.derichs.vet/`; 5 MB HTTP cache for ETag support
- **`ui/MedicationViewModel.kt`** — Single ViewModel for the whole app; exposes `StateFlow` for search, loading, language, notes, data status; no DI framework
- **`ui/screens/`** — Four screens: `MedicationListScreen`, `MedicationDetailScreen`, `GeneralNotesScreen`, `AboutScreen`
- **`utils/`** — `LanguageManager` (SharedPreferences, FR/NL), `JsonLoader` (Gson, assets + version.json), `NotesManager` (export/import via FileProvider), `SearchNormalizer` (normalise + Jaro-Winkler)

### Navigation

Compose Navigation with four routes defined in `MainActivity.kt`:
- `medication_list` — searchable list, language switch, refresh, export/import notes
- `medication_detail/{medicationId}` — detail + per-medication notes
- `generalNotes` — single global note
- `about` — version info, author, disclaimer

### Language switching

Does **not** use Android's locale system. Language choice (FR default, NL) is stored via
`LanguageManager` in SharedPreferences. Switching is **offline-first**: `ensureLanguageLoaded()`
checks Room then assets — no network call. After switching, `activity.recreate()` is triggered
via `_shouldRecreateActivity` StateFlow so string resources reload in the new locale.

### Offline-first data loading

On first launch, `MedicationRepository.initializePrimaryLanguage()` populates Room from
bundled assets (`vet_medications_fr.json` / `vet_medications_nl.json`). The secondary language
is loaded in the background via `initializeSecondaryLanguages()`. Refreshes hit the server
only when `version.json` reports a newer version than what is stored in SharedPreferences.

Asset provenance is stored separately (`asset_version_<lang>`, `asset_published_at_<lang>`)
so it never interferes with the online version comparison. The data status row in the UI
shows whether data came from assets (never synced) or a real online refresh.

### Search

Five-tier in-memory ranking in `MedicationRepository.rankSearch()`: exact name (100),
starts-with (90), contains (75), token match (65), composition contains (35).
Fuzzy track: Jaro-Winkler ≥ 0.85 on name tokens shown below a divider.
`SearchNormalizer` regex objects are hoisted to object-level `val`s (compiled once).
Room is subscribed once per language switch; ranking runs in-memory from the cached list.

### Data status (SharedPreferences keys per language)

```
data_version_<lang>          Long    — online version; 0 = never synced
data_published_at_<lang>     String  — YYYY/MM/DD from server
last_checked_at_<lang>       Long    — epoch ms of last version.json check
asset_version_<lang>         Long    — version bundled in assets
asset_published_at_<lang>    String  — YYYY/MM/DD from assets/version.json
```

`getDataStatus()` prefers online keys; falls back to asset keys; returns null only if neither exists.

### Notes export/import

`NotesManager` writes a versioned text file (Format: 2) and shares it via `FileProvider`.
Export includes the general note (`=== GENERAL NOTE ===` block) followed by per-medication
notes. Import is a state-machine line parser that round-trips both. Format version guards
against importing incompatible old files. File provider authority: `vet.derichs.compendium.fileprovider`.

### Background updates

`UpdateWorker` (WorkManager, daily, requires network + battery not low) refreshes all
supported languages via `refreshAllLanguages()`. Network/IO failures use `Result.retry()`
for WorkManager exponential backoff; invalid-payload failures use `Result.failure()`.

### Dependencies (version catalog)

Managed in `gradle/libs.versions.toml`. Key versions: Compose BOM 2024.09.00, Room 2.8.4,
Navigation Compose 2.8.8, Retrofit 2.11.0, WorkManager 2.10.0, Kotlin Coroutines 1.10.1,
KSP for Room annotation processing.

### Database migrations

Current version: **5**. History: v1→v2 added `medication_notes`; v2→v3 added `general_note`;
v3→v4 added `language` column + composite PK (id, language); v4→v5 added composite index.
Add a new `Migration` object when changing schema; **never use `fallbackToDestructiveMigration`** —
user notes live in this database and must survive upgrades.

### R8 / obfuscation

`isMinifyEnabled = true` and `isShrinkResources = true` are set for release builds.
`proguard-rules.pro` keeps Gson model classes (`Medication`, `VersionInfo`, `LanguageData`)
and `UpdateWorker` (class name resolved by string). All other libraries ship their own
consumer ProGuard rules.

---

## Server-side context (outside this repo)

Data is produced by a separate pipeline (`compendium-scripts` on Forgejo):
a nightly Forgejo Action launches an ephemeral OVH instance which scrapes
vetcompendium.be, commits changed JSON, and pushes a tarball over SSH to an LXC
serving `https://medicament.derichs.vet/`.

Endpoints the app consumes:
- `GET /version.json` — metadata; changes only when data changes
- `GET /vet_medications_fr.json` — ~1.1 MB, array of medication objects
- `GET /vet_medications_nl.json` — ~1.1 MB, array of medication objects

`app/src/main/assets/version.json` must be kept in sync with the bundled medication JSONs.
When the pipeline updates the assets, copy the new `version.json` into assets and commit it.

---

## Open PRs (as of 2026-09-13)

- **feature/asset-data-status** — surfaces asset data provenance in UI; date normalization to YYYY/MM/DD
- **feature/enable-r8-obfuscation** — enables R8 minification for Google Play ≥25% requirement
- **feature/fix-translations-and-general-note-export** — Dutch error messages; general note in export/import

---

## Pending work

- **refactor-2 item 4b** — precompute `nameNormalized` / `compositionNormalized` columns in
  the `Medication` entity to eliminate per-keystroke normalization of 1,570+ rows.
  Requires DB migration 5→6 and updating `replaceAllForLanguage()` to populate the columns.
  `rankSearch()` should fall back to runtime normalization when columns are null (migration
  leaves existing rows null; populated on first data refresh).

---

## Invariants — do not violate

- **Never leave the medication table empty or partial.** Any replace must be transactional
  (`replaceAllForLanguage` is `@Transaction`) and must refuse payloads below 80% of current count.
- **Persist the data version only after a verified successful insert.** Never before.
- **The app must be fully usable with no network**, including switching FR↔NL.
- **Never wipe user notes.** `medication_notes` and `general_note` are user data; schema
  changes need explicit `Migration` objects.
- **Surface data age in the UI.** Silent staleness is the failure mode this project is
  recovering from — the server pipeline was broken for nine months undetected.
- **Asset version must never be used in the update comparison.** `asset_version_<lang>` is
  informational only; `data_version_<lang>` drives the refresh decision.
- Keep it dependency-light: no DI framework, no analytics, no telemetry. GPLv3, offline, private.

## Working conventions

- Work on one feature branch at a time.
- Run `./gradlew assembleDebug` before considering a task done.
- Prefer complete file rewrites over fragmentary edits when a file changes substantially.
- Ask before adding a new third-party dependency.
- Do not add `logcat.txt`, `refactor-*.md`, `CLAUDE_orig.md`, or `RELEASE_NOTES.txt` to git — they are in `.gitignore`.
