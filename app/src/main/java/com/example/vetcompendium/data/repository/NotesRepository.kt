package com.example.vetcompendium.data.repository

import com.example.vetcompendium.data.database.MedicationNoteDao
import com.example.vetcompendium.data.model.MedicationNote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotesRepository(private val noteDao: MedicationNoteDao) {

    fun getNoteForMedication(medicationId: String): Flow<String> {
        return noteDao.getNoteForMedicationFlow(medicationId)
            .map { note -> note?.notes ?: "" }
    }

    suspend fun saveNote(medicationId: String, notes: String) {
        if (notes.trim().isEmpty()) {
            noteDao.deleteNoteForMedication(medicationId)
        } else {
            val note = MedicationNote(
                medicationId = medicationId,
                notes = notes.trim(),
                lastModified = System.currentTimeMillis()
            )
            noteDao.insertNote(note)
        }
    }

    suspend fun getAllNotes(): List<MedicationNote> {
        return noteDao.getAllNotes()
    }

    suspend fun deleteAllNotes() {
        noteDao.deleteAllNotes()
    }
}
