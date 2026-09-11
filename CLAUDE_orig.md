# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

Does **not** use Android's locale system. Language choice (FR default, NL) is stored via `LanguageManager` in SharedPreferences. Switching reloads the localized dataset from the API and triggers `activity.recreate()` via `_shouldRecreateActivity` StateFlow.

### Offline-first data loading

On first launch, `MedicationRepository` populates Room from bundled assets (`vet_medications_fr.json` / `vet_medications_nl.json`). Subsequent refreshes hit `https://medicament.derichs.vet/vet_medications_{lang}.json`. Version is checked via `/version.json` before downloading.

### Dependencies (version catalog)

Managed in `gradle/libs.versions.toml`. Key versions: Compose BOM 2024.09.00, Room 2.8.4, Navigation Compose 2.8.8, Retrofit 2.11.0, WorkManager 2.10.0, Kotlin Coroutines 1.10.1, KSP for Room annotation processing.

### Database migrations

`MedicationDatabase.kt` contains explicit migrations (v1→v2: added `medication_notes` table). Add a new `Migration` object when changing schema; do not use `fallbackToDestructiveMigration`.

### Notes export

`NotesManager` writes a formatted text file to `files/notes/` and shares it via `FileProvider` (configured in `AndroidManifest.xml`). The file provider authority is `vet.derichs.compendium.fileprovider`.
