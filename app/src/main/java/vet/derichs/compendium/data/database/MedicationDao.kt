package vet.derichs.compendium.data.database

import androidx.room.*
import vet.derichs.compendium.data.model.Medication
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MedicationDao {

    @Query("SELECT * FROM medications WHERE language = :language ORDER BY name ASC")
    abstract fun getAllMedications(language: String): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id AND language = :language")
    abstract suspend fun getMedicationById(id: String, language: String): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(medications: List<Medication>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(medication: Medication)

    @Update
    abstract suspend fun update(medication: Medication)

    @Delete
    abstract suspend fun delete(medication: Medication)

    @Query("DELETE FROM medications WHERE language = :language")
    abstract suspend fun deleteByLanguage(language: String)

    @Query("SELECT COUNT(*) FROM medications WHERE language = :language")
    abstract suspend fun getCountForLanguage(language: String): Int

    @Transaction
    open suspend fun replaceAllForLanguage(language: String, medications: List<Medication>) {
        deleteByLanguage(language)
        insertAll(medications)
    }
}
