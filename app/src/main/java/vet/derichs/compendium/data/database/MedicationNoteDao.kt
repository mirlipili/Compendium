package vet.derichs.compendium.data.database

import androidx.room.*
import vet.derichs.compendium.data.model.MedicationNote
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationNoteDao {
    @Query("SELECT * FROM medication_notes WHERE medicationId = :medicationId")
    suspend fun getNoteForMedication(medicationId: String): MedicationNote?

    @Query("SELECT * FROM medication_notes WHERE medicationId = :medicationId")
    fun getNoteForMedicationFlow(medicationId: String): Flow<MedicationNote?>

    @Query("SELECT * FROM medication_notes")
    suspend fun getAllNotes(): List<MedicationNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: MedicationNote)

    @Delete
    suspend fun deleteNote(note: MedicationNote)

    @Query("DELETE FROM medication_notes WHERE medicationId = :medicationId")
    suspend fun deleteNoteForMedication(medicationId: String)

    @Query("DELETE FROM medication_notes")
    suspend fun deleteAllNotes()
}
