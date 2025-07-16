package vet.derichs.compendium.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import vet.derichs.compendium.data.model.Medication

class NetworkRepository(private val context: Context) {
    private val apiService = MedicationApiService.create()
    private val prefs = context.getSharedPreferences("vet_app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "NetworkRepository"
        private const val LAST_VERSION_KEY = "last_data_version"
        private const val LAST_LANGUAGE_KEY = "last_updated_language"
    }

    suspend fun checkForUpdates(language: String): Result<Boolean> {
        return try {
            if (!isNetworkAvailable()) {
                return Result.failure(Exception("No network connection"))
            }

            Log.d(TAG, "Checking for data updates for language: $language")
            val response = apiService.getVersionInfo()

            if (response.isSuccessful) {
                val versionInfo = response.body()
                if (versionInfo != null) {
                    val currentVersion = getLastKnownVersion(language)
                    val serverVersion = versionInfo.version

                    Log.d(TAG, "Language: $language, Current version: $currentVersion, Server version: $serverVersion")

                    val hasUpdate = serverVersion > currentVersion
                    if (hasUpdate) {
                        Log.d(TAG, "Update available for $language! Date: ${versionInfo.human_readable_date}")
                    } else {
                        Log.d(TAG, "No updates available for $language")
                    }

                    Result.success(hasUpdate)
                } else {
                    Result.failure(Exception("Invalid version response"))
                }
            } else {
                Result.failure(Exception("Server error: ${response.code()}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            Result.failure(e)
        }
    }

    suspend fun fetchMedicationsFromServer(language: String): Result<List<Medication>> {
        return try {
            if (!isNetworkAvailable()) {
                return Result.failure(Exception("No network connection"))
            }

            Log.d(TAG, "Fetching medications from server for language: $language")

            // Use direct method calls instead of extension function
            val response = when(language) {
                "fr" -> apiService.getMedicationsFr()
                "nl" -> apiService.getMedicationsNl()
                else -> apiService.getMedicationsFr()
            }

            if (response.isSuccessful) {
                val medications = response.body()
                if (medications != null && medications.isNotEmpty()) {
                    Log.d(TAG, "Successfully fetched ${medications.size} medications for $language")

                    // Update version after successful fetch
                    updateVersionFromServer(language)

                    Result.success(medications)
                } else {
                    Log.w(TAG, "Server returned empty medication list for $language")
                    Result.failure(Exception("Server returned empty data"))
                }
            } else {
                Log.e(TAG, "Server error: ${response.code()} - ${response.message()}")
                Result.failure(Exception("Server error: ${response.code()}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Network error while fetching medications for $language", e)
            Result.failure(e)
        }
    }

    private suspend fun updateVersionFromServer(language: String) {
        try {
            val versionResponse = apiService.getVersionInfo()
            if (versionResponse.isSuccessful) {
                versionResponse.body()?.let { versionInfo ->
                    saveLastKnownVersion(language, versionInfo.version)
                    Log.d(TAG, "Updated local version for $language to: ${versionInfo.version}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not update version info for $language", e)
        }
    }

    private fun getLastKnownVersion(language: String): Long {
        return prefs.getLong("${LAST_VERSION_KEY}_$language", 0L)
    }

    private fun saveLastKnownVersion(language: String, version: Long) {
        prefs.edit()
            .putLong("${LAST_VERSION_KEY}_$language", version)
            .putString(LAST_LANGUAGE_KEY, language)
            .apply()
    }

    fun hasLocalData(language: String): Boolean {
        return getLastKnownVersion(language) > 0
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
