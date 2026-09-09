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
- **`data/database/`** — Room database (v3) with DAOs returning `Flow<>` for reactivity
- **`data/repository/`** — `MedicationRepository` (offline-first: assets → Room, optional remote refresh) and `NotesRepository`
- **`data/network/`** — Retrofit service hitting `https://medicament.derichs.vet/` for versioned JSON endpoints
- **`ui/MedicationViewModel.kt`** — Single ViewModel for the whole app; exposes `StateFlow` for search, loading, language, notes; no DI framework, repositories are manually instantiated in `init`
- **`ui/screens/`** — Three screens: `MedicationListScreen`, `MedicationDetailScreen`, `GeneralNotesScreen`
- **`utils/`** — `LanguageManager` (SharedPreferences, FR/NL), `JsonLoader` (Gson, assets), `NotesManager` (export via FileProvider)

### Navigation

Compose Navigation with three routes defined in `MainActivity.kt`:
- `medication_list` — searchable list, language switch, refresh, export
- `medication_detail/{medicationId}` — detail + per-medication notes
- `generalNotes` — single global note

### Language switching

Does **not** use Android's locale system. Language choice (FR default, NL) is stored via
`LanguageManager` in SharedPreferences. Switching currently reloads the localized dataset
from the API and triggers `activity.recreate()` via `_shouldRecreateActivity` StateFlow.
(See Known Issues — this network dependency is a bug, not a design goal.)

### Offline-first data loading

On first launch, `MedicationRepository` populates Room from bundled assets
(`vet_medications_fr.json` / `vet_medications_nl.json`). Refreshes hit
`https://medicament.derichs.vet/vet_medications_{lang}.json`.

**IMPORTANT:** despite the presence of `NetworkRepository.checkForUpdates()` and a
`/version.json` endpoint, **no version check happens on the live code path**. Every
refresh downloads ~1.1 MB unconditionally. See Known Issues #1.

### Dependencies (version catalog)

Managed in `gradle/libs.versions.toml`. Key versions: Compose BOM 2024.09.00, Room 2.8.4,
Navigation Compose 2.8.8, Retrofit 2.11.0, WorkManager 2.10.0, Kotlin Coroutines 1.10.1,
KSP for Room annotation processing.

### Database migrations

`MedicationDatabase.kt` contains explicit migrations (v1→v2: added `medication_notes` table).
Add a new `Migration` object when changing schema; **do not use `fallbackToDestructiveMigration`** —
user notes live in this database and must survive upgrades.

### Notes export

`NotesManager` writes a formatted text file to `files/notes/` and shares it via `FileProvider`
(configured in `AndroidManifest.xml`). The file provider authority is `vet.derichs.compendium.fileprovider`.

---

## Server-side context (outside this repo)

Data is produced by a separate pipeline (`compendium-scripts` on Forgejo):
a nightly Forgejo Action launches an ephemeral OVH instance which scrapes
vetcompendium.be, commits changed JSON, and pushes a tarball over SSH to an LXC
serving `https://medicament.derichs.vet/`.

Endpoints the app consumes:

- `GET /version.json` — small metadata file, changes only when data actually changes
- `GET /vet_medications_fr.json` — ~1.1 MB, array of medication objects
- `GET /vet_medications_nl.json` — ~1.1 MB, array of medication objects

Served by nginx as static files, so `ETag` / `Last-Modified` are available and
`If-None-Match` conditional requests will return 304.

`version.json` is only republished when new medications appear, which can be days or
weeks apart. Do not treat an unchanged version as an error condition.

---

## Known issues (verified by code audit — do not "rediscover", fix)

1. **`NetworkRepository` is dead code.** It contains a complete, working version-check
   implementation (`checkForUpdates()`, `getLastKnownVersion()`, `saveLastKnownVersion()`),
   but has zero references outside its own file. The live path
   (`ViewModel.refreshData()` → `MedicationRepository.refreshCurrentLanguage()` →
   `fetchFromServer()`) never reads `version.json` and never saves a version.
   This is why the app re-downloads everything on every refresh.

2. **Destructive refresh with no floor.** `MedicationRepository.fetchFromServer()` calls
   `medicationDao.deleteAll()` then `insertAll()`, not wrapped in `@Transaction`. It guards
   only on `isNotEmpty()`. A truncated-but-valid server response of 3 items would wipe
   ~1570 records and install 3. A mid-insert exception leaves the DB partial with no rollback.

3. **Language switching requires network.** `switchLanguage()` → `refreshFromServerWithLanguage()`
   → `fetchFromServer()`. Offline users cannot switch FR↔NL, even though both datasets ship
   in `assets/`. The `medications` table holds one language at a time and is wiped on switch.

4. **`LanguageManager.detectAndSetDefaultLanguage()` never detects.**
   `getCurrentLanguage()` returns `DEFAULT_LANGUAGE` ("fr") when the pref is unset, so the
   `if (savedLanguage.isNotEmpty()) return` guard always fires and the system-locale branch
   is unreachable. Dutch-speaking users always get French on first launch.
   Fix by checking `prefs.contains(KEY_LANGUAGE)`.

5. **Three conflicting version models.** `data/model/Version.kt`,
   `data/model/VersionInfo.kt`, and a third `VersionInfo` declared inside
   `MedicationApiService.kt`. Only the third is used. Delete the dead two.

6. **Gson vs Kotlin null-safety.** Gson uses reflection and ignores Kotlin non-null types.
   `VersionInfo.languages: Map<String, LanguageData>` and `Medication.id: String` are
   declared non-null; a missing field yields a null in a non-null type and an NPE at first
   access, swallowed by a generic catch. Validate parsed payloads explicitly.

7. **`UpdateWorker` is a stub.** `// TODO: Implement update checking logic here`, returns
   `Result.success()`, and no `WorkManager` enqueue call exists anywhere in the codebase.

8. **Search has no ranking, folding, or fuzziness.** `MedicationDao.searchMedications()` is
   `LIKE '%q%'` across `name`, `firm`, `target_species`, `composition`, ordered `name ASC`.
   An exact name match sorts alphabetically among composition-substring hits. `Métacam`
   and `metacam` are different searches. Room FTS is available but unused.

9. **Minor.** Two separate Retrofit instances (one in `MedicationRepository`, one in
   `NetworkRepository`); no OkHttp cache, so no ETag/conditional-request support;
   `hasLocalData()` is dead and would always return false.

---

## Invariants — do not violate

- **Never leave the medication table empty or partial.** Any replace must be transactional
  and must refuse payloads below a sanity floor (reject if new count < 80% of current count,
  unless current count is 0).
- **Persist the data version only after a verified successful insert**, never before parsing
  or during download. Recording a version for data that failed to land makes the app
  permanently believe it is current.
- **The app must be fully usable with no network**, including switching FR↔NL.
- **Never wipe user notes.** `medication_notes` and `general_note` are user data; schema
  changes need explicit `Migration` objects.
- **Surface data age in the UI.** Store and display `dataVersion`, `dataPublishedAt`, and
  `lastCheckedAt`. Silent staleness is the failure mode this project is actively recovering
  from — the server pipeline was broken for nine months and the app never indicated anything.
- Keep it dependency-light: no DI framework, no analytics, no telemetry. GPLv3, offline, private.

## Working conventions

- Work on one feature branch at a time; see `REFACTOR-PLAN.md` for the ordered task list.
- Run `./gradlew assembleDebug` before considering a task done.
- Prefer complete file rewrites over fragmentary edits when a file changes substantially.
- Ask before adding a new third-party dependency.
