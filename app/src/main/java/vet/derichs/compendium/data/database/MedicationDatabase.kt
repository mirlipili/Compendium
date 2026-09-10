package vet.derichs.compendium.data.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import vet.derichs.compendium.data.model.GeneralNote
import vet.derichs.compendium.data.model.Medication
import vet.derichs.compendium.data.model.MedicationNote

@Database(
    entities = [Medication::class, MedicationNote::class, GeneralNote::class],
    version = 4,
    exportSchema = false
)
abstract class MedicationDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationNoteDao(): MedicationNoteDao
    abstract fun generalNoteDao(): GeneralNoteDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS medication_notes (" +
                        "medicationId TEXT NOT NULL, " +
                        "notes TEXT NOT NULL, " +
                        "lastModified INTEGER NOT NULL, " +
                        "PRIMARY KEY(medicationId))"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS general_note (" +
                        "id INTEGER NOT NULL, " +
                        "content TEXT NOT NULL, " +
                        "PRIMARY KEY(id))"
                )
            }
        }

        // Adds language column and composite primary key (id, language).
        // Existing medication rows are dropped — initializeData() repopulates
        // both languages from assets on the next launch.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS medications_new (" +
                        "id TEXT NOT NULL, " +
                        "language TEXT NOT NULL, " +
                        "name TEXT, " +
                        "firm TEXT, " +
                        "registration_number TEXT, " +
                        "target_species TEXT, " +
                        "composition TEXT, " +
                        "pharmaceutical_form TEXT, " +
                        "administration_route TEXT, " +
                        "posology TEXT, " +
                        "withdrawal_period TEXT, " +
                        "packaging TEXT, " +
                        "prescription TEXT, " +
                        "rcp_link TEXT, " +
                        "PRIMARY KEY(id, language))"
                )
                database.execSQL("DROP TABLE medications")
                database.execSQL("ALTER TABLE medications_new RENAME TO medications")
            }
        }
    }
}
