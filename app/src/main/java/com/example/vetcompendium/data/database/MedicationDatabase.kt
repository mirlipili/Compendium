package com.example.vetcompendium.data.database

import android.content.Context
import androidx.room.*
import com.example.vetcompendium.data.model.Medication
import com.example.vetcompendium.data.model.MedicationNote
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


@Database(
    entities = [Medication::class, MedicationNote::class],
    version = 2, // Increment version
    exportSchema = false
)
abstract class MedicationDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationNoteDao(): MedicationNoteDao

    companion object {
        @Volatile
        private var INSTANCE: MedicationDatabase? = null

        fun getDatabase(context: Context): MedicationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedicationDatabase::class.java,
                    "medication_database"
                )
                    .addMigrations(MIGRATION_1_2) // Add migration
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medication_notes` (" +
                            "`medicationId` TEXT NOT NULL, " +
                            "`notes` TEXT NOT NULL, " +
                            "`lastModified` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`medicationId`))"
                )
            }
        }
    }
}
