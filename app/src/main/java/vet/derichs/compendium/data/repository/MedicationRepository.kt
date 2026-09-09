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
import vet.derichs.compendium.utils.JsonLoader
import vet.derichs.compendium.utils.LanguageManager
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
    private val apiService: MedicationApiService = MedicationApiService.create(cacheDir)
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

    // On first launch (or after a schema migration that wiped the table), populate
    // all supported languages from bundled assets so offline use is immediately available.
    suspend fun initializeData() {
        withContext(Dispatchers.IO) {
            try {
                for (language in LanguageManager.SUPPORTED_LANGUAGES) {
                    val count = medicationDao.getCountForLanguage(language)
                    if (count == 0) {
                        Log.d(TAG, "No data for $language — loading from assets")
                        prePopulateFromAssets(language)
                    } else {
                        Log.d(TAG, "$count medications already loaded for $language")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing data", e)
                throw e
            }
        }
    }

    private suspend fun prePopulateFromAssets(language: String) {
        try {
            val medications = JsonLoader.loadMedicationsFromAssets(context, language)
            if (!medications.isNullOrEmpty()) {
                val tagged = medications.map { it.copy(language = language) }
                medicationDao.insertAll(tagged)
                // Clear stored version so the next online refresh re-downloads from the server
                // rather than skipping because a stale version number appears to match.
                prefs.edit()
                    .remove("data_version_$language")
                    .remove("data_published_at_$language")
                    .apply()
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
                prefs.edit().putLong("last_checked_at", now).apply()
                Log.d(TAG, "Data for $language is up to date (v${versionInfo.version})")
                return Result.success("Données à jour")
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
                .putLong("last_checked_at", now)
                .apply()

            Log.d(TAG, "Stored ${taggedList.size} medications for $language (v${versionInfo.version})")
            Result.success("${taggedList.size} médicaments mis à jour")

        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing $language", e)
            Result.failure(e)
        }
    }

    fun getDataStatus(language: String): DataStatus? {
        val version = prefs.getLong("data_version_$language", 0L)
        if (version == 0L) return null
        return DataStatus(
            dataVersion = version,
            dataPublishedAt = prefs.getString("data_published_at_$language", "") ?: "",
            lastCheckedAt = prefs.getLong("last_checked_at", 0L)
        )
    }

    fun getAllMedications(language: String): Flow<List<Medication>> =
        medicationDao.getAllMedications(language)

    fun searchMedications(query: String, language: String): Flow<List<Medication>> =
        medicationDao.searchMedications("%$query%", language)
}
