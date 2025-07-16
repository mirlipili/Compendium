package vet.derichs.compendium.data.database

import androidx.room.*
import vet.derichs.compendium.data.model.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE name LIKE :searchQuery OR firm LIKE :searchQuery OR target_species LIKE :searchQuery OR composition LIKE :searchQuery ORDER BY name ASC")
    fun searchMedications(searchQuery: String): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: String): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medications: List<Medication>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: Medication)

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)

    @Query("DELETE FROM medications")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun getCount(): Int

    @Query("SELECT * FROM medications LIMIT :limit")
    suspend fun getFirstFew(limit: Int): List<Medication>
}
