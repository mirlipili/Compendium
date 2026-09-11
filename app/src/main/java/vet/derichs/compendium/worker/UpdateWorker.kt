package vet.derichs.compendium.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import vet.derichs.compendium.data.database.MedicationDatabase
import vet.derichs.compendium.data.repository.MedicationRepository
import vet.derichs.compendium.utils.LanguageManager

// Runs once a day (when connected + battery not low) to check for new medication data.
// Strategy: check-and-download — the version check hits only a few bytes; the full
// ~1.1 MB download happens only when the server version actually changed (typically
// days or weeks apart), so the cost on metered connections is acceptable.
// On any failure the existing database is left untouched; the staleness indicator
// in the UI will alert the user on next open.
class UpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UpdateWorker"
        const val WORK_NAME = "vet_daily_update_check"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting background update check")
            val database = MedicationDatabase.getDatabase(context)
            val repository = MedicationRepository(
                medicationDao = database.medicationDao(),
                generalNoteDao = database.generalNoteDao(),
                context = context,
                cacheDir = context.cacheDir
            )

            val result = repository.refreshCurrentLanguage()
            result.fold(
                onSuccess = { message ->
                    Log.d(TAG, "Background update: $message")
                    Result.success()
                },
                onFailure = { exception ->
                    Log.w(TAG, "Background update failed: ${exception.message}")
                    Result.failure()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during background update", e)
            Result.failure()
        }
    }
}
