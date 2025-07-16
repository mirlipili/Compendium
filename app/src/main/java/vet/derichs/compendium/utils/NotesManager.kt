package vet.derichs.compendium.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import vet.derichs.compendium.data.database.MedicationNoteDao
import vet.derichs.compendium.data.model.Medication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class NotesManager(
    private val context: Context,
    private val noteDao: MedicationNoteDao
) {
    companion object {
        private const val TAG = "NotesManager"
    }

    suspend fun exportNotes(medications: List<Medication>): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val notes = noteDao.getAllNotes()
                if (notes.isEmpty()) {
                    return@withContext Result.failure(Exception("No notes to export"))
                }

                val medicationMap = medications.associateBy { it.id }
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                val exportText = buildString {
                    appendLine("=== VET COMPENDIUM NOTES ===")
                    appendLine("Export Date: ${dateFormat.format(Date())}")
                    appendLine("Total Notes: ${notes.size}")
                    appendLine()

                    notes.forEach { note ->
                        val medication = medicationMap[note.medicationId]
                        val medicationName = medication?.name ?: "Unknown Medication (${note.medicationId})"

                        appendLine("===== $medicationName =====")
                        appendLine("Last Modified: ${dateFormat.format(Date(note.lastModified))}")
                        appendLine()
                        appendLine(note.notes)
                        appendLine()
                        appendLine("=".repeat(50)) // Fix: use repeat instead of *
                        appendLine()
                    }
                }

                val fileName = "vet_compendium_notes_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
                val file = File(context.getExternalFilesDir(null), fileName)
                file.writeText(exportText)

                // Share the file
                shareFile(file)

                Result.success("Notes exported to $fileName")

            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                Result.failure(e)
            }
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Vet Compendium Notes")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share Notes")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to share file", e)
        }
    }
}
