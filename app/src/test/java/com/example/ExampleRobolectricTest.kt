package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.catalog.NasaCatalog
import com.example.data.local.VerifiedNasaArchive
import com.example.data.model.AggregationMode
import com.example.data.model.AnalysisStatus
import com.example.data.model.DataProvenanceStatus
import com.example.data.model.InvestigationConfig
import com.example.data.model.QualityStatus
import com.example.data.model.TrendDirection
import com.example.data.model.TrendMethod
import com.example.domain.analysis.TrendAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches Earth Pulse`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Earth Pulse", appName)
    }

    @Test
    fun `overview renders and launches investigation flow`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("EARTH PULSE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("overview_start_investigation_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("overview_start_investigation_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stepper_step_1").assertIsDisplayed()
    }

    @Test
    fun `all 6 curated regions load verbatim NASA POWER snapshots and preserve fill gaps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val t2m = NasaCatalog.variableFor("T2M")

        NasaCatalog.regions.forEach { region ->
            assertTrue("Missing cache manifest entry for ${region.id}", VerifiedNasaArchive.hasCachedSeries(region.id))
            val series = VerifiedNasaArchive.getCachedSeries(
                context = context,
                regionId = region.id,
                variable = t2m,
                startYear = 1990,
                endYear = 2024,
            )
            assertEquals("Expected 35*12 monthly points for ${region.id}", 35 * 12, series.size)
            assertTrue("Expected valid NASA T2M values for ${region.id}", series.all { it.value != null })
        }

        // Verify authentic NASA CERES pre-1984 fill values (-999.0) in Svalbard solar irradiance
        val swDwn = NasaCatalog.variableFor("ALLSKY_SFC_SW_DWN")
        val swSeries = VerifiedNasaArchive.getCachedSeries(
            context = context,
            regionId = "svalbard_arctic",
            variable = swDwn,
            startYear = 1981,
            endYear = 1985,
        )
        val jan1982 = swSeries.first { it.yearMonth == "1982-01" }
        assertEquals(QualityStatus.MISSING, jan1982.qualityStatus)
        assertNull(jan1982.value)
        val jul1984 = swSeries.first { it.yearMonth == "1984-07" }
        assertEquals(QualityStatus.VALID, jul1984.qualityStatus)
        assertNotNull(jul1984.value)
    }

    @Test
    fun `real NASA POWER Svalbard T2M 1990-2024 analysis computes significant warming trend`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val region = NasaCatalog.regionFor("svalbard_arctic")
        val variable = NasaCatalog.variableFor("T2M")
        val dataset = NasaCatalog.datasetFor(variable.datasetId)
        val raw = VerifiedNasaArchive.getCachedSeries(
            context = context,
            regionId = region.id,
            variable = variable,
            startYear = 1990,
            endYear = 2024,
        )
        val config = InvestigationConfig(
            id = "test_real_svalbard",
            title = "Real Svalbard T2M",
            region = region,
            variableIds = listOf("T2M"),
            startYear = 1990,
            endYear = 2024,
            aggregation = AggregationMode.DESEASONALIZED_ANOMALY,
            trendMethod = TrendMethod.SEASONAL_MANN_KENDALL,
        )
        val result = TrendAnalyzer.analyze(
            rawPoints = raw,
            variable = variable,
            dataset = dataset,
            region = region,
            config = config,
            provenanceStatus = DataProvenanceStatus.CACHED_NASA_REFERENCE,
            retrievedAtIso = VerifiedNasaArchive.ARCHIVE_SNAPSHOT_TIMESTAMP,
        )

        assertEquals(AnalysisStatus.SUCCESS, result.status)
        assertEquals(TrendDirection.INCREASING, result.trendDirection)
        assertTrue(result.isStatisticallySignificant)
        assertTrue((result.slopePerDecade ?: 0.0) > 0.0)
    }
}
