package vet.derichs.compendium.data.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import vet.derichs.compendium.data.model.Medication
import java.io.File

data class VersionInfo(
    val version: Long,
    val human_readable_date: String,
    val languages: Map<String, LanguageData>?
) {
    fun isValid(): Boolean =
        version > 0 && human_readable_date.isNotBlank()
}

data class LanguageData(
    val data_path: String?,
    val display_name: String?
)

interface MedicationApiService {
    @GET("version.json")
    suspend fun getVersionInfo(): Response<VersionInfo>

    @GET("vet_medications_fr.json")
    suspend fun getMedicationsFr(): Response<List<Medication>>

    @GET("vet_medications_nl.json")
    suspend fun getMedicationsNl(): Response<List<Medication>>

    companion object {
        private const val BASE_URL = "https://medicament.derichs.vet/"

        @Volatile private var INSTANCE: MedicationApiService? = null

        fun getInstance(cacheDir: File): MedicationApiService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(cacheDir).also { INSTANCE = it }
            }

        private fun create(cacheDir: File): MedicationApiService {
            val cache = Cache(File(cacheDir, "http_cache"), 5L * 1024 * 1024)
            val client = OkHttpClient.Builder()
                .cache(cache)
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MedicationApiService::class.java)
        }
    }
}
