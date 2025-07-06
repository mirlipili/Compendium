package com.example.vetcompendium.utils

import android.content.Context
import android.util.Log
import com.example.vetcompendium.data.model.Medication
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object JsonLoader {
    private const val TAG = "JsonLoader"

    fun loadMedicationsFromAssets(context: Context, language: String = "fr"): List<Medication>? {
        return try {
            val filename = "medications_$language.json"
            Log.d(TAG, "Loading medications from assets: $filename")

            val jsonString = context.assets.open(filename).bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<Medication>>() {}.type
            val medications = Gson().fromJson<List<Medication>>(jsonString, listType)

            Log.d(TAG, "Successfully loaded ${medications?.size ?: 0} medications from $filename")
            medications

        } catch (e: Exception) {
            Log.e(TAG, "Error loading medications from assets for language $language", e)
            null
        }
    }
}
