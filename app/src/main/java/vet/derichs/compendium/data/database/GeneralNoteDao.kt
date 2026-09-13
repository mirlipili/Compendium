package vet.derichs.compendium.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import vet.derichs.compendium.data.model.GeneralNote

@Dao
interface GeneralNoteDao {
    // "Upsert" will INSERT if it's new, or UPDATE if it already exists. Perfect for our case.
    @Upsert
    suspend fun upsert(note: GeneralNote)

    // Gets the one note as a Flow, so the UI updates automatically.
    // We use LIMIT 1 just to be safe.
    @Query("SELECT * FROM general_notes WHERE id = 1 LIMIT 1")
    fun getGeneralNote(): Flow<GeneralNote?>

    @Query("SELECT * FROM general_notes WHERE id = 1 LIMIT 1")
    suspend fun getGeneralNoteOnce(): GeneralNote?
}
