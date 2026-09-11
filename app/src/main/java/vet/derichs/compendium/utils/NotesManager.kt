package vet.derichs.compendium.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import vet.derichs.compendium.data.database.MedicationNoteDao
import vet.derichs.compendium.data.model.Medication
import vet.derichs.compendium.data.model.MedicationNote
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
        private const val SEPARATOR = "=================================================="  // 50 =
        private const val FORMAT_VERSION = "2"
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
                    appendLine("Format: $FORMAT_VERSION")
                    appendLine("Export Date: ${dateFormat.format(Date())}")
                    appendLine("Total Notes: ${notes.size}")
                    appendLine()

                    notes.forEach { note ->
                        val medication = medicationMap[note.medicationId]
                        val medicationName = medication?.name ?: "Unknown (${note.medicationId})"

                        appendLine("===== $medicationName =====")
                        appendLine("Medication ID: ${note.medicationId}")
                        appendLine("Last Modified: ${dateFormat.format(Date(note.lastModified))}")
                        appendLine()
                        appendLine(note.notes)
                        appendLine()
                        appendLine(SEPARATOR)
                        appendLine()
                    }
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "vet_compendium_notes_$timestamp.txt"
                val file = File(context.getExternalFilesDir(null), fileName)
                file.writeText(exportText)

                shareFile(file)
                Result.success(fileName)

            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                Result.failure(e)
            }
        }
    }

    // Returns the number of notes successfully imported.
    // Requires Format 2 files (exported by this version of the app).
    suspend fun importNotes(uri: Uri): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
                    ?: return@withContext Result.failure(Exception("Cannot read file"))

                if (!content.contains("Format: $FORMAT_VERSION")) {
                    return@withContext Result.failure(
                        Exception("Unsupported format — please re-export with the current app version")
                    )
                }

                // Parse line by line using a simple state machine.
                // Each note block: ===== name ===== / Medication ID: xxx / Last Modified: yyy /
                //                  <blank> / content lines / <blank> / SEPARATOR
                var currentId: String? = null
                val contentBuf = StringBuilder()
                var inContent = false
                var imported = 0

                suspend fun flush() {
                    val id = currentId ?: return
                    val text = contentBuf.toString().trim()
                    if (text.isNotBlank()) {
                        noteDao.insertNote(
                            MedicationNote(
                                medicationId = id,
                                notes = text,
                                lastModified = System.currentTimeMillis()
                            )
                        )
                        imported++
                    }
                    currentId = null
                    contentBuf.clear()
                    inContent = false
                }

                for (line in content.lines()) {
                    when {
                        line.trimEnd() == SEPARATOR -> flush()

                        line.startsWith("Medication ID:") ->
                            currentId = line.removePrefix("Medication ID:").trim()

                        line.startsWith("Last Modified:") ->
                            inContent = false  // next blank line marks start of content

                        // Note name header — flush any in-progress note (shouldn't normally happen)
                        line.startsWith("=====") && line.endsWith("=====") && !line.all { it == '=' } -> {
                            flush()
                        }

                        // First blank line after metadata → content starts on the next non-blank line
                        line.isBlank() && !inContent && currentId != null ->
                            inContent = true

                        inContent ->
                            contentBuf.appendLine(line)

                        else -> { /* header / metadata lines — skip */ }
                    }
                }
                flush()  // handle file without trailing separator

                if (imported == 0) {
                    Result.failure(Exception("No notes found in file"))
                } else {
                    Result.success(imported)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
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
            val chooser = Intent.createChooser(shareIntent, "Share Notes")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share file", e)
        }
    }
}
