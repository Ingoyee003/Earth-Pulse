package com.example.data.remote

import com.example.data.model.QualityStatus
import com.example.data.model.TimeSeriesPoint
import com.example.data.model.VariableDefinition
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class NasaPowerResponse(
    val properties: NasaPowerProperties? = null,
)

@JsonClass(generateAdapter = true)
data class NasaPowerProperties(
    val parameter: Map<String, Map<String, Double>>? = null,
)

interface NasaPowerApi {
    @GET("api/temporal/monthly/point")
    suspend fun getMonthlyPoint(
        @Query("parameters") parameters: String,
        @Query("community") community: String = "AG",
        @Query("longitude") longitude: Double,
        @Query("latitude") latitude: Double,
        @Query("start") startYear: Int,
        @Query("end") endYear: Int,
        @Query("format") format: String = "JSON",
    ): NasaPowerResponse
}

object NasaPowerClient {
    private const val BASE_URL = "https://power.larc.nasa.gov/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    val api: NasaPowerApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NasaPowerApi::class.java)
    }

    fun parseMonthlyParameter(
        response: NasaPowerResponse,
        variable: VariableDefinition,
    ): List<TimeSeriesPoint> {
        val paramCode = variable.nasaPowerParam ?: return emptyList()
        val rawMap = response.properties?.parameter?.get(paramCode) ?: return emptyList()
        return rawMap.entries
            .mapNotNull { (key, rawVal) ->
                if (key.length != 6) return@mapNotNull null
                val year = key.substring(0, 4).toIntOrNull() ?: return@mapNotNull null
                val month = key.substring(4, 6).toIntOrNull() ?: return@mapNotNull null
                if (month !in 1..12) return@mapNotNull null // Skip YYYY13 annual summary key

                val isFill = !rawVal.isFinite() || rawVal <= -900.0
                val outOfRange = !isFill && (rawVal < variable.validMin || rawVal > variable.validMax)
                val status = when {
                    isFill -> QualityStatus.MISSING
                    outOfRange -> QualityStatus.OUT_OF_RANGE
                    else -> QualityStatus.VALID
                }
                val ym = String.format(java.util.Locale.US, "%04d-%02d", year, month)
                TimeSeriesPoint(
                    yearMonth = ym,
                    year = year,
                    month = month,
                    decimalYear = year + (month - 0.5) / 12.0,
                    rawValue = if (isFill) null else rawVal,
                    value = if (status == QualityStatus.VALID) rawVal else null,
                    qualityStatus = status,
                    sourceFlag = if (isFill) "FILL_999" else "NASA_POWER_OK",
                )
            }
            .sortedBy { it.decimalYear }
    }
}
