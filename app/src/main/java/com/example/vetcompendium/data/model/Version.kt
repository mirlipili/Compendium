package com.example.vetcompendium.data.model

import com.google.gson.annotations.SerializedName

data class Version(
    val version: Long,
    @SerializedName("human_readable_date")
    val humanReadableDate: String,
    @SerializedName("data_path")
    val dataPath: String
)
