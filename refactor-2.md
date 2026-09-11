# REVIEW-2.md — post-refactor audit

Audit of `main` after items 1–4. Ordered by severity. Items 1 and 2 are live bugs.

---

## 1. `AppDatabase.kt` is a loaded gun — delete it

`data/database/AppDatabase.kt` still exists and declares:

```kotlin
@Database(entities = [Medication::class], version = 1)
Room.databaseBuilder(context, AppDatabase::class.java, "medication_database")
```

It points at **the same database file name** as `MedicationDatabase` ("medication_database")
but declares schema version 1 with a single entity and no migrations. It currently has zero
references outside its own file, so it is inert today — but any future call to
`AppDatabase.getDatabase()` opens a v5 database expecting v1 and throws
(`IllegalStateException: Room cannot verify the data integrity` / downgrade error), on a
user's device, with their notes inside.

It also makes KSP generate a second, redundant Room implementation.

**Do:** delete `data/database/AppDatabase.kt`. There is exactly one database in this app.

---

## 2. Multiple OkHttp `Cache` instances over the same directory

`MedicationRepository` builds its own API service in the constructor:

```kotlin
private val apiService = MedicationApiService.create(cacheDir)
// → Cache(File(cacheDir, "http_cache"), 5MB)
```

and `MedicationRepository` is instantiated in **three** places that can be alive at once:
`MedicationViewModel.init`, `UpdateWorker.doWork()`, and again on every
`activity.recreate()` after a language switch.

OkHttp's `Cache` wraps a `DiskLruCache` with a journal file and an exclusive lock. Two live
`Cache` objects over the same directory in the same process is explicitly unsupported —
it produces journal corruption or `IllegalStateException`, and the old instances are never
closed, so each language switch leaks one.

**Do:** make the API service a process-wide singleton.

```kotlin
companion object {
    @Volatile private var INSTANCE: MedicationApiService? = null

    fun getInstance(cacheDir: File): MedicationApiService =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: create(cacheDir).also { INSTANCE = it }
        }
}
```

Call `getInstance(...)` from `MedicationRepository`; keep `create()` private.

---

## 3. Search regression: `firm` and `target_species` are no longer searchable

The old DAO query searched `name`, `firm`, `target_species`, and `composition`.
`rankSearch()` only scores `name` and `composition`. Searching a manufacturer or a species
("bovins", "porcins") now returns nothing.

**Do:** decide deliberately, then implement. Suggested tiers if keeping them:

| Match | Score |
|-------|-------|
| exact normalized name | 100 |
| name starts-with | 90 |
| name contains | 75 |
| all query tokens match name tokens | 65 |
| composition contains | 35 |
| target_species contains | 25 |
| firm contains | 20 |

Species below composition so a molecule search is not drowned by every bovine product.

---

## 4. Search does far more work per keystroke than it needs to

Three compounding problems:

**(a) Regex objects are recompiled on every call.** `SearchNormalizer.normalize()` constructs
three `Regex(...)` objects inline each invocation. Hoist them to `private val` properties of
the object — they are immutable and thread-safe.

**(b) Every medication is normalized on every keystroke.** `rankSearch` normalizes `name`
and `composition` for all ~1,570 rows in the direct pass, then normalizes `name` again for
non-matching rows in the fuzzy pass. That is ~4,700 normalize calls per query.

Fix properly by precomputing at insert time (this was in the original plan and was not done):
add `nameNormalized` and `compositionNormalized` columns to the `Medication` entity, populate
them in the same `.copy()` that sets `language`, and add a Room migration. Ranking then
compares pre-normalized strings and only the query is normalized at search time.

**(c) The whole table is re-read from Room on every keystroke.** In the ViewModel,
`flatMapLatest` on `(query, language)` cancels and re-subscribes to
`repository.getAllMedications(lang)` each time the query changes, so SQLite re-runs the query
and Room re-maps ~1,570 objects per keystroke. Subscribe once per language instead:

```kotlin
private val allMedications: StateFlow<List<Medication>> =
    _currentLanguage
        .flatMapLatest { lang -> repository.getAllMedications(lang) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

private val _searchResult: StateFlow<SearchResult> =
    combine(
        allMedications,
        _searchQuery.debounce { if (it.isBlank()) 0L else 200L }.distinctUntilChanged()
    ) { meds, query ->
        if (query.isBlank()) SearchResult(meds, emptyList())
        else withContext(Dispatchers.Default) { repository.rankSearch(query, meds) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResult.EMPTY)
```

(a) alone is a one-line-per-regex change with a large payoff; do it even if (b) is deferred.

---

## 5. Asset-loaded languages report no data status at all

`prePopulateFromAssets()` deliberately clears `data_version_<lang>`, and `getDataStatus()`
returns `null` when the stored version is 0. So a language loaded from bundled assets — which
is every language on first install, and the secondary language until its first online refresh —
shows **no data age in the UI at all**.

Silent staleness is precisely the failure this project is recovering from. An empty indicator
is the same user experience as the nine-month outage.

**Do:** record the asset build version rather than clearing it. Write the version from the
bundled `version.json` (ship it in `assets/`, or hardcode a `BuildConfig` constant at build
time) and flag the status as asset-sourced, e.g. `DataStatus(..., fromAssets = true)`, so the
UI can render "Données intégrées à l'app (v20260904) — jamais actualisées en ligne".

Keep the current re-download behaviour: asset data must never be mistaken for a completed
online sync.

---

## 6. `lastCheckedAt` is global while `dataVersion` is per-language

`refreshForLanguage()` writes `last_checked_at` as a single shared key, but `getDataStatus(lang)`
pairs it with that language's version. After refreshing FR, the NL status screen claims NL was
checked moments ago when it was not.

**Do:** store `last_checked_at_<language>` alongside the other per-language keys.

---

## 7. `UpdateWorker`: wrong failure mode, and it only refreshes one language

**(a)** Transient network failures return `Result.failure()`, which means no retry until the
next daily window. Use `Result.retry()` for network/IO exceptions (WorkManager applies
exponential backoff) and reserve `failure()` for permanent problems such as a rejected payload.

**(b)** It calls `refreshCurrentLanguage()`, so the non-active language never updates in the
background. Since both languages are now offline-switchable, a user who switches to NL after
weeks of FR usage gets stale NL data. Refresh all of `LanguageManager.SUPPORTED_LANGUAGES`,
or document the choice explicitly.

**(c)** Minor: the worker stores `private val context` while `CoroutineWorker` already exposes
`applicationContext`. Harmless, but redundant — drop the property.

---

## Verified good — no action needed

- `replaceAllForLanguage` is correctly `@Transaction`-wrapped; delete+insert now rolls back as a unit.
- The 0.8 sanity floor, empty-list rejection, and version-persisted-only-after-successful-insert ordering are all correct.
- `MIGRATION_3_4` / `MIGRATION_4_5` are sound; `medication_notes` and `general_note` survive, and the declared entity index name matches the one created in the migration.
- `detectAndSetDefaultLanguage()` now checks `prefs.contains(KEY_LANGUAGE)` — system-locale detection actually runs.
- Language switching is genuinely network-free (`ensureLanguageLoaded` → DB, then assets).
- `VersionInfo.languages` is correctly nullable, matching the real server payload; `isValid()` guards the fields that matter.
- Dead models (`Version.kt`, `data/model/VersionInfo.kt`) and `NetworkRepository` are gone.
- Jaro-Winkler implementation is correct, including the per-token comparison that stops long
  product names from diluting a brand-name typo match.
