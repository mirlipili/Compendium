package vet.derichs.compendium.utils

import android.content.Context
import android.util.Log
import vet.derichs.compendium.data.model.Medication
import vet.derichs.compendium.data.network.VersionInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object JsonLoader {
    private const val TAG = "JsonLoader"

    fun loadMedicationsFromAssets(context: Context, language: String = "fr"): List<Medication>? {
        return try {
            val filename = "vet_medications_$language.json"
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

    fun loadVersionFromAssets(context: Context): VersionInfo? {
        return try {
            val jsonString = context.assets.open("version.json").bufferedReader().use { it.readText() }
            val info = Gson().fromJson(jsonString, VersionInfo::class.java)
            // Trim ISO datetime to date-only: "2026-09-04T03:04:19…" → "2026-09-04"
            val dateOnly = info?.human_readable_date?.substringBefore('T') ?: return null
            info.copy(human_readable_date = dateOnly)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading version.json from assets", e)
            null
        }
    }
}
