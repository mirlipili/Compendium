package vet.derichs.compendium.worker

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import vet.derichs.compendium.data.database.MedicationDatabase
import vet.derichs.compendium.data.repository.MedicationRepository
import java.io.IOException

// Runs once a day (when connected + battery not low) to check for new medication data.
// Strategy: check-and-download — the version check hits only a few bytes; the full
// ~1.1 MB download happens only when the server version actually changed (typically
// days or weeks apart), so the cost on metered connections is acceptable.
// On any failure the existing database is left untouched; the staleness indicator
// in the UI will alert the user on next open.
class UpdateWorker(
    context: android.content.Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UpdateWorker"
        const val WORK_NAME = "vet_daily_update_check"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting background update check")
            val database = MedicationDatabase.getDatabase(applicationContext)
            val repository = MedicationRepository(
                medicationDao = database.medicationDao(),
                generalNoteDao = database.generalNoteDao(),
                context = applicationContext,
                cacheDir = applicationContext.cacheDir
            )

            var shouldRetry = false
            repository.refreshAllLanguages().forEach { (lang, result) ->
                result.fold(
                    onSuccess = { message -> Log.d(TAG, "[$lang] $message") },
                    onFailure = { exception ->
                        Log.w(TAG, "[$lang] update failed: ${exception.message}")
                        if (exception is IOException) shouldRetry = true
                    }
                )
            }

            if (shouldRetry) Result.retry() else Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Network error during background update — will retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during background update", e)
            Result.failure()
        }
    }
}
