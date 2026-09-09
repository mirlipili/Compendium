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

    suspend fun initializeData() {
        withContext(Dispatchers.IO) {
            try {
                val count = medicationDao.getCount()
                val currentLanguage = languageManager.getCurrentLanguage()
                Log.d(TAG, "Current medication count: $count, language: $currentLanguage")
                if (count == 0) {
                    Log.d(TAG, "No medications found, pre-populating from assets...")
                    prePopulateFromAssets(currentLanguage)
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
                medicationDao.insertAll(medications)
                Log.d(TAG, "Stored ${medications.size} medications from assets.")
            } else {
                Log.w(TAG, "Could not load medications from assets.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pre-populating from assets", e)
        }
    }

    suspend fun refreshCurrentLanguage(): Result<String> =
        withContext(Dispatchers.IO) {
            refreshForLanguage(languageManager.getCurrentLanguage())
        }

    suspend fun refreshFromServerWithLanguage(language: String): Result<String> =
        withContext(Dispatchers.IO) {
            refreshForLanguage(language)
        }

    private suspend fun refreshForLanguage(language: String): Result<String> {
        return try {
            // Step 1: fetch and validate version.json
            val versionResponse = apiService.getVersionInfo()
            if (!versionResponse.isSuccessful) {
                return Result.failure(
                    Exception("Erreur serveur : ${versionResponse.code()}")
                )
            }
            val versionInfo = versionResponse.body()
            if (versionInfo == null || !versionInfo.isValid()) {
                return Result.failure(Exception("Réponse version invalide"))
            }

            val now = System.currentTimeMillis()
            val storedVersion = prefs.getLong("data_version_$language", 0L)

            // Step 2: compare versions
            if (versionInfo.version == storedVersion) {
                prefs.edit().putLong("last_checked_at", now).apply()
                Log.d(TAG, "Data for $language is already up to date (v${versionInfo.version})")
                return Result.success("Données à jour")
            }

            // Step 3: version is newer — download and validate
            Log.d(TAG, "New version available for $language: ${versionInfo.version}")
            val medResponse = when (language.lowercase()) {
                "nl" -> apiService.getMedicationsNl()
                else -> apiService.getMedicationsFr()
            }
            if (!medResponse.isSuccessful) {
                return Result.failure(
                    Exception("Erreur téléchargement : ${medResponse.code()}")
                )
            }
            val newList: List<Medication> = medResponse.body() ?: emptyList()
            if (newList.isEmpty()) {
                return Result.failure(Exception("Le serveur a renvoyé une liste vide"))
            }
            val currentCount = medicationDao.getCount()
            if (currentCount > 0 && newList.size < currentCount * MIN_VALID_FRACTION) {
                return Result.failure(
                    Exception(
                        "Données suspectes (${newList.size} reçus vs $currentCount en base) — " +
                            "données locales conservées"
                    )
                )
            }

            // Step 4: atomic replace then persist metadata
            medicationDao.replaceAll(newList)
            prefs.edit()
                .putLong("data_version_$language", versionInfo.version)
                .putString("data_published_at_$language", versionInfo.human_readable_date)
                .putLong("last_checked_at", now)
                .apply()

            Log.d(TAG, "Stored ${newList.size} medications for $language (v${versionInfo.version})")
            Result.success("${newList.size} médicaments mis à jour")

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

    fun getAllMedications(): Flow<List<Medication>> = medicationDao.getAllMedications()

    fun searchMedications(query: String): Flow<List<Medication>> =
        medicationDao.searchMedications("%$query%")
}
