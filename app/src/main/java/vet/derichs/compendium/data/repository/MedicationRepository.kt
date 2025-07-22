package vet.derichs.compendium.data.repository

import android.content.Context
import android.util.Log
import vet.derichs.compendium.data.database.MedicationDao
import vet.derichs.compendium.data.database.GeneralNoteDao
import vet.derichs.compendium.data.model.Medication
import vet.derichs.compendium.data.model.GeneralNote
import vet.derichs.compendium.data.network.MedicationApiService
import vet.derichs.compendium.utils.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val context: Context,
    private val generalNoteDao: GeneralNoteDao
) {
    private val apiService: MedicationApiService = MedicationApiService.create()
    private val languageManager = LanguageManager(context)

    companion object {
        private const val TAG = "MedicationRepository"
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
                    Log.d(TAG, "No medications found, fetching from server...")
                    fetchFromServer(currentLanguage)
                } else {
                    Log.d(TAG, "Medications exist in database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing data", e)
                throw e
            }
        }
    }

    suspend fun refreshCurrentLanguage(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val currentLanguage = languageManager.getCurrentLanguage()
                Log.d(TAG, "Refreshing data for language: $currentLanguage")

                fetchFromServer(currentLanguage)
                Result.success("Data refreshed successfully for $currentLanguage")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing data", e)
                Result.failure(e)
            }
        }
    }

    suspend fun refreshFromServerWithLanguage(language: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Switching to language: $language")

                // First clear existing data since we're switching languages
                medicationDao.deleteAll()
                Log.d(TAG, "Cleared existing medications for language switch")

                // Fetch data in the new language
                fetchFromServer(language)

                Result.success("Data loaded successfully for $language")
            } catch (e: Exception) {
                Log.e(TAG, "Error switching language", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun fetchFromServer(language: String) {
        try {
            Log.d(TAG, "Fetching medications from server for language: $language")

            val response = when (language.lowercase()) {
                "nl" -> {
                    Log.d(TAG, "Using Dutch API endpoint")
                    apiService.getMedicationsNl()
                }
                "fr" -> {
                    Log.d(TAG, "Using French API endpoint")
                    apiService.getMedicationsFr()
                }
                else -> {
                    Log.w(TAG, "Unknown language: $language, defaulting to French")
                    apiService.getMedicationsFr()
                }
            }

            if (response.isSuccessful) {
                val medications = response.body() ?: emptyList()
                Log.d(TAG, "Server response: ${medications.size} medications")

                if (medications.isNotEmpty()) {
                    // Clear existing data before inserting new data
                    medicationDao.deleteAll()

                    // Insert new medications
                    medicationDao.insertAll(medications)
                    Log.d(TAG, "Successfully stored ${medications.size} medications in database")
                } else {
                    Log.w(TAG, "No medications received from server")
                }
            } else {
                Log.e(TAG, "API request failed: ${response.code()} - ${response.message()}")
                throw Exception("API request failed: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from server for language: $language", e)
            throw e
        }
    }

    fun getAllMedications(): Flow<List<Medication>> = medicationDao.getAllMedications()

    fun searchMedications(query: String): Flow<List<Medication>> =
        medicationDao.searchMedications("%$query%")

}
