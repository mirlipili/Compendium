package com.example.vetcompendium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_notes")
data class MedicationNote(
    @PrimaryKey
    val medicationId: String,
    val notes: String,
    val lastModified: Long = System.currentTimeMillis()
)
