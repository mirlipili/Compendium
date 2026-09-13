package vet.derichs.compendium.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import vet.derichs.compendium.data.database.GeneralNoteDao
import vet.derichs.compendium.data.database.MedicationDao
import vet.derichs.compendium.data.model.GeneralNote
import vet.derichs.compendium.data.model.Medication
import vet.derichs.compendium.data.network.MedicationApiService
import vet.derichs.compendium.ui.DataStatus
import vet.derichs.compendium.ui.SearchResult
import vet.derichs.compendium.utils.JsonLoader
import vet.derichs.compendium.utils.LanguageManager
import vet.derichs.compendium.utils.SearchNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val context: Context,
    private val generalNoteDao: GeneralNoteDao,
    cacheDir: File
) {
    private val apiService: MedicationApiService = MedicationApiService.getInstance(cacheDir)
    private val languageManager = LanguageManager(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vet_data_status", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "MedicationRepository"
        private const val MIN_VALID_FRACTION = 0.8
    }

    val generalNote: Flow<GeneralNote?> = generalNoteDao.getGeneralNote()

    suspend fun saveGeneralNote(content: String) {
        generalNoteDao.upsert(GeneralNote(content = content))
    }

    // Loads the primary language and returns so the UI can become interactive.
    // Call initializeSecondaryLanguages() afterwards in a separate coroutine.
    suspend fun initializePrimaryLanguage(language: String) {
        withContext(Dispatchers.IO) {
            try {
                val count = medicationDao.getCountForLanguage(language)
                if (count == 0) {
                    Log.d(TAG, "No data for $language — loading from assets")
                    prePopulateFromAssets(language)
                } else {
                    Log.d(TAG, "$count medications already loaded for $language")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing primary language $language", e)
                throw e
            }
        }
    }

    // Loads any language not yet in the DB, without blocking the UI.
    suspend fun initializeSecondaryLanguages(primaryLanguage: String) {
        withContext(Dispatchers.IO) {
            for (language in LanguageManager.SUPPORTED_LANGUAGES) {
                if (language == primaryLanguage) continue
                try {
                    val count = medicationDao.getCountForLanguage(language)
                    if (count == 0) {
                        Log.d(TAG, "Background-loading $language from assets")
                        prePopulateFromAssets(language)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error background-loading $language", e)
                    // Non-fatal: primary language is already available
                }
            }
        }
    }

    private suspend fun prePopulateFromAssets(language: String) {
        try {
            val medications = JsonLoader.loadMedicationsFromAssets(context, language)
            if (!medications.isNullOrEmpty()) {
                val tagged = medications.map { it.copy(language = language) }
                medicationDao.insertAll(tagged)
                // Clear online version so the next refresh always re-downloads — asset version
                // must never be mistaken for a completed online sync.
                val edit = prefs.edit()
                    .remove("data_version_$language")
                    .remove("data_published_at_$language")
                // Store asset provenance separately for UI display only.
                val assetVersion = JsonLoader.loadVersionFromAssets(context)
                if (assetVersion != null) {
                    edit.putLong("asset_version_$language", assetVersion.version)
                        .putString("asset_published_at_$language", assetVersion.human_readable_date)
                }
                edit.apply()
                Log.d(TAG, "Stored ${tagged.size} medications from assets for $language")
            } else {
                Log.w(TAG, "No medications found in assets for $language")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading assets for $language", e)
        }
    }

    // Ensures the target language is present in the database without touching the network.
    // Called by the ViewModel when the user switches language.
    suspend fun ensureLanguageLoaded(language: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val count = medicationDao.getCountForLanguage(language)
                if (count > 0) {
                    Result.success("$count médicaments disponibles")
                } else {
                    Log.d(TAG, "Language $language not in DB — loading from assets")
                    prePopulateFromAssets(language)
                    val newCount = medicationDao.getCountForLanguage(language)
                    if (newCount > 0) {
                        Result.success("$newCount médicaments chargés")
                    } else {
                        Result.failure(Exception("Données introuvables pour $language"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error ensuring language $language", e)
                Result.failure(e)
            }
        }

    suspend fun refreshCurrentLanguage(): Result<String> =
        withContext(Dispatchers.IO) {
            refreshForLanguage(languageManager.getCurrentLanguage())
        }

    suspend fun refreshAllLanguages(): List<Pair<String, Result<String>>> =
        withContext(Dispatchers.IO) {
            LanguageManager.SUPPORTED_LANGUAGES.map { lang ->
                lang to refreshForLanguage(lang)
            }
        }

    private suspend fun refreshForLanguage(language: String): Result<String> {
        return try {
            val versionResponse = apiService.getVersionInfo()
            if (!versionResponse.isSuccessful) {
                return Result.failure(Exception("Erreur serveur : ${versionResponse.code()}"))
            }
            val versionInfo = versionResponse.body()
            if (versionInfo == null || !versionInfo.isValid()) {
                return Result.failure(Exception("Réponse version invalide"))
            }

            val now = System.currentTimeMillis()
            val storedVersion = prefs.getLong("data_version_$language", 0L)

            if (versionInfo.version == storedVersion) {
                prefs.edit().putLong("last_checked_at_$language", now).apply()
                Log.d(TAG, "Data for $language is up to date (v${versionInfo.version})")
                return Result.success(if (language == "nl") "Gegevens actueel" else "Données à jour")
            }

            Log.d(TAG, "New version for $language: ${versionInfo.version}")
            val medResponse = when (language.lowercase()) {
                "nl" -> apiService.getMedicationsNl()
                else -> apiService.getMedicationsFr()
            }
            if (!medResponse.isSuccessful) {
                return Result.failure(Exception("Erreur téléchargement : ${medResponse.code()}"))
            }
            val newList: List<Medication> = medResponse.body() ?: emptyList()
            if (newList.isEmpty()) {
                return Result.failure(Exception("Le serveur a renvoyé une liste vide"))
            }
            val currentCount = medicationDao.getCountForLanguage(language)
            if (currentCount > 0 && newList.size < currentCount * MIN_VALID_FRACTION) {
                return Result.failure(
                    Exception(
                        "Données suspectes (${newList.size} reçus vs $currentCount en base) — " +
                            "données locales conservées"
                    )
                )
            }

            val taggedList = newList.map { it.copy(language = language) }
            medicationDao.replaceAllForLanguage(language, taggedList)
            prefs.edit()
                .putLong("data_version_$language", versionInfo.version)
                .putString("data_published_at_$language", versionInfo.human_readable_date)
                .putLong("last_checked_at_$language", now)
                .apply()

            Log.d(TAG, "Stored ${taggedList.size} medications for $language (v${versionInfo.version})")
            Result.success(
                if (language == "nl") "${taggedList.size} geneesmiddelen bijgewerkt"
                else "${taggedList.size} médicaments mis à jour"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing $language", e)
            Result.failure(e)
        }
    }

    fun getDataStatus(language: String): DataStatus? {
        val onlineVersion = prefs.getLong("data_version_$language", 0L)
        if (onlineVersion > 0L) {
            return DataStatus(
                dataVersion = onlineVersion,
                dataPublishedAt = prefs.getString("data_published_at_$language", "") ?: "",
                lastCheckedAt = prefs.getLong("last_checked_at_$language", 0L),
                isFromAssets = false
            )
        }
        val assetVersion = prefs.getLong("asset_version_$language", 0L)
        if (assetVersion > 0L) {
            return DataStatus(
                dataVersion = assetVersion,
                dataPublishedAt = prefs.getString("asset_published_at_$language", "") ?: "",
                lastCheckedAt = 0L,
                isFromAssets = true
            )
        }
        return null
    }

    fun getAllMedications(language: String): Flow<List<Medication>> =
        medicationDao.getAllMedications(language)

    /**
     * Rank [medications] against [rawQuery] using a five-tier system:
     *   1. exact normalized name            (100)
     *   2. name starts-with query           (90)
     *   3. name contains query              (75)
     *   4. all query tokens match name tokens (65)
     *   5. composition contains query       (35)
     * Jaro-Winkler ≥ 0.85 on the name is returned separately as [SearchResult.fuzzy].
     * All comparisons use [SearchNormalizer.normalize] on both sides.
     */
    fun rankSearch(rawQuery: String, medications: List<Medication>): SearchResult {
        val nq = SearchNormalizer.normalize(rawQuery)
        if (nq.isBlank()) return SearchResult(medications, emptyList())

        val queryTokens = nq.split(" ").filter { it.isNotEmpty() }

        data class Scored(val med: Medication, val score: Int)

        val direct = mutableListOf<Scored>()
        val directKeys = mutableSetOf<Pair<String, String>>()

        for (med in medications) {
            val nn = SearchNormalizer.normalize(med.name ?: "")
            val nc = SearchNormalizer.normalize(med.composition ?: "")
            val nameTokens = nn.split(" ").filter { it.isNotEmpty() }
            val compTokens = nc.split(" ").filter { it.isNotEmpty() }

            val score = when {
                nn == nq -> 100
                nn.startsWith(nq) -> 90
                nn.contains(nq) -> 75
                queryTokens.isNotEmpty() &&
                    queryTokens.all { qt -> nameTokens.any { it.startsWith(qt) } } -> 65
                nc.contains(nq) ||
                    (queryTokens.isNotEmpty() &&
                        queryTokens.all { qt -> compTokens.any { it.startsWith(qt) } }) -> 35
                else -> 0
            }

            if (score > 0) {
                direct.add(Scored(med, score))
                directKeys.add(med.id to med.language)
            }
        }

        val fuzzy = mutableListOf<Scored>()
        for (med in medications) {
            if ((med.id to med.language) in directKeys) continue
            val nn = SearchNormalizer.normalize(med.name ?: "")
            // Compare against each token so a long name ("metacam 5 mg ml solution...")
            // doesn't penalise a typo match on the brand-name word via the |s2| term.
            val nameTokens = nn.split(" ").filter { it.isNotEmpty() }
            val jw = nameTokens.maxOfOrNull { SearchNormalizer.jaroWinkler(nq, it) } ?: 0.0
            if (jw >= 0.85) {
                val score = (40 + (jw - 0.85) / 0.15 * 20).toInt().coerceIn(40, 60)
                fuzzy.add(Scored(med, score))
            }
        }

        return SearchResult(
            direct = direct
                .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.med.name?.lowercase() ?: "" })
                .map { it.med },
            fuzzy = fuzzy
                .sortedByDescending { it.score }
                .map { it.med }
        )
    }
}
