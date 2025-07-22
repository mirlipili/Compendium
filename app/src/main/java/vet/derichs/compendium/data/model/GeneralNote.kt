package vet.derichs.compendium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "general_notes")
data class GeneralNote(
    // We'll use a fixed ID of 1 to ensure we only ever have one general note.
    @PrimaryKey val id: Int = 1,
    val content: String
)
