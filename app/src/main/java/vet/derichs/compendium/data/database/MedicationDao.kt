package vet.derichs.compendium.data.database

import androidx.room.*
import vet.derichs.compendium.data.model.Medication
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MedicationDao {

    @Query("SELECT * FROM medications ORDER BY name ASC")
    abstract fun getAllMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE name LIKE :searchQuery OR firm LIKE :searchQuery OR target_species LIKE :searchQuery OR composition LIKE :searchQuery ORDER BY name ASC")
    abstract fun searchMedications(searchQuery: String): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    abstract suspend fun getMedicationById(id: String): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(medications: List<Medication>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(medication: Medication)

    @Update
    abstract suspend fun update(medication: Medication)

    @Delete
    abstract suspend fun delete(medication: Medication)

    @Query("DELETE FROM medications")
    abstract suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM medications")
    abstract suspend fun getCount(): Int

    @Query("SELECT * FROM medications LIMIT :limit")
    abstract suspend fun getFirstFew(limit: Int): List<Medication>

    @Transaction
    open suspend fun replaceAll(medications: List<Medication>) {
        deleteAll()
        insertAll(medications)
    }
}
