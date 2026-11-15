package com.example.data.local

import android.content.Context
import com.example.data.model.TimeSeriesPoint
import com.example.data.model.VariableDefinition
import com.example.data.remote.NasaPowerClient
import com.example.data.remote.NasaPowerResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Loads verbatim pre-fetched NASA POWER API JSON snapshots from app/src/main/assets/nasa_cached/.
 * Contains zero synthetic or approximated data—every value is an authentic NASA POWER (MERRA-2 / CERES) record.
 */
object VerifiedNasaArchive {

    const val ARCHIVE_SNAPSHOT_TIMESTAMP = "2026-11-15T09:28:00Z"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(NasaPowerResponse::class.java)

    private val supportedRegionIds = setOf(
        "svalbard_arctic",
        "amazon_central",
        "sahel_west",
        "california_central_valley",
        "ganges_delta",
        "murray_darling",
    )

    fun hasCachedSeries(regionId: String): Boolean = regionId in supportedRegionIds

    fun getCachedSeries(
        context: Context,
        regionId: String,
        variable: VariableDefinition,
        startYear: Int,
        endYear: Int,
    ): List<TimeSeriesPoint> {
        if (!hasCachedSeries(regionId)) return emptyList()
        val jsonText = runCatching {
            context.assets.open("nasa_cached/$regionId.json")
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull() ?: return emptyList()

        val parsedResponse = runCatching { responseAdapter.fromJson(jsonText) }.getOrNull()
            ?: return emptyList()

        return NasaPowerClient.parseMonthlyParameter(parsedResponse, variable)
            .filter { it.year in startYear..endYear }
    }
}
