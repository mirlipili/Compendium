package com.example.vetcompendium.data.network

import com.example.vetcompendium.data.model.Medication
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class VersionInfo(
    val version: Long,
    val human_readable_date: String,
    val languages: Map<String, LanguageData>
)

data class LanguageData(
    val data_path: String,
    val display_name: String
)

interface MedicationApiService {
    @GET("version.json")
    suspend fun getVersionInfo(): Response<VersionInfo>

    @GET("vet_medications_fr.json")
    suspend fun getMedicationsFr(): Response<List<Medication>>

    @GET("vet_medications_nl.json")
    suspend fun getMedicationsNl(): Response<List<Medication>>

    companion object {
        // Replace with your actual base URL
        private const val BASE_URL = "https://medicament.derichs.vet/"

        fun create(): MedicationApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MedicationApiService::class.java)
        }
    }
}
