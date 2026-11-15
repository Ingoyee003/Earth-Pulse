package com.example.data.ai

import com.example.data.catalog.NasaCatalog
import com.example.data.model.InvestigationResult
import com.example.data.model.VariableDefinition
import com.example.domain.analysis.TrendAnalyzer
import java.util.Locale

data class AiExplanationResponse(
    val isPlaceholderMode: Boolean,
    val notice: String,
    val headline: String,
    val summaryParagraph: String,
    val statisticalCaveat: String,
    val sourceCitation: String,
)

data class AiVariableSuggestion(
    val isPlaceholderMode: Boolean,
    val notice: String,
    val suggestedVariableIds: List<String>,
    val rationale: String,
)

/**
 * Grounded AI explainability interface (Spec Section 12).
 * Statistical calculations remain 100% deterministic in TrendAnalyzer;
 * AI only translates structured computed outputs or suggests catalog variables.
 */
interface AiRepository {
    suspend fun explainResult(result: InvestigationResult): AiExplanationResponse
    suspend fun suggestVariables(question: String): AiVariableSuggestion
}

/**
 * Placeholder implementation active when Firebase AI Logic is not provisioned.
 * Explicitly labels outputs as sample/placeholder responses grounded only in
 * computed numbers and dataset metadata without calling any external LLM API.
 */
class PlaceholderAiRepository : AiRepository {

    companion object {
        const val FIREBASE_REQUIRED_NOTICE =
            "AI Assistant Preview (Placeholder Mode): Live Gemini natural-language explanations require Firebase AI Logic to be enabled."
    }

    override suspend fun explainResult(result: InvestigationResult): AiExplanationResponse {
        val primary = result.primaryResult
        val slopeText = primary.slopePerDecade?.let {
            String.format(Locale.US, "%+.3f %s", it, primary.slopeUnit)
        } ?: "unavailable"
        val pText = TrendAnalyzer.formatPValue(primary.pValue)
        val missingPct = String.format(Locale.US, "%.1f%%", primary.missingFraction * 100.0)

        val secondaryLine = result.secondaryResult?.let { sec ->
            val secSlope = sec.slopePerDecade?.let {
                String.format(Locale.US, "%+.3f %s", it, sec.slopeUnit)
            } ?: "unavailable"
            " Alongside this, ${sec.variable.displayName} shows an estimated rate of $secSlope (p = ${TrendAnalyzer.formatPValue(sec.pValue)})."
        }.orEmpty()

        val summary = buildString {
            append("Question investigated: Long-term tendency of ${primary.variable.displayName} in ${primary.region.name} (${result.config.startYear}–${result.config.endYear}). ")
            append("Observed direction is ${primary.trendDirection.label.lowercase(Locale.US)} at an estimated rate of $slopeText under ${primary.slopeEstimatorName}. ")
            append("Using ${primary.methodName}, p = $pText against α = ${primary.significanceThreshold}: ${primary.interpretationText}")
            append(secondaryLine)
        }

        val caveat = buildString {
            append("Missing/flagged observations account for $missingPct of the series (${primary.nMissingOrFlagged} of ${primary.nTotalSlots} slots) and were never converted to zero. ")
            if (primary.hasAutocorrelationCaveat) {
                append("Elevated lag-1 serial correlation was detected, so treat p-values conservatively. ")
            }
            append(primary.dataset.limitations)
        }

        return AiExplanationResponse(
            isPlaceholderMode = true,
            notice = FIREBASE_REQUIRED_NOTICE,
            headline = primary.neutralTitle,
            summaryParagraph = summary,
            statisticalCaveat = caveat,
            sourceCitation = "${primary.dataset.name} (${primary.productVersion}) — ${primary.dataset.documentationUrl}",
        )
    }

    override suspend fun suggestVariables(question: String): AiVariableSuggestion {
        val q = question.lowercase(Locale.US)
        val matched = mutableListOf<VariableDefinition>()

        if (q.contains("warm") || q.contains("heat") || q.contains("temp") || q.contains("arctic") || q.contains("hot")) {
            matched.add(NasaCatalog.variableFor("T2M"))
            matched.add(NasaCatalog.variableFor("TS"))
        }
        if (q.contains("rain") || q.contains("precip") || q.contains("drought") || q.contains("monsoon") || q.contains("flood") || q.contains("wet")) {
            matched.add(NasaCatalog.variableFor("PRECTOTCORR"))
            matched.add(NasaCatalog.variableFor("GWETTOP"))
        }
        if (q.contains("humid") || q.contains("moisture") || q.contains("vapor") || q.contains("soil")) {
            matched.add(NasaCatalog.variableFor("RH2M"))
            matched.add(NasaCatalog.variableFor("GWETTOP"))
        }
        if (q.contains("solar") || q.contains("sun") || q.contains("cloud") || q.contains("irrad") || q.contains("energy")) {
            matched.add(NasaCatalog.variableFor("ALLSKY_SFC_SW_DWN"))
        }
        if (q.contains("skin") || q.contains("surface")) {
            matched.add(NasaCatalog.variableFor("TS"))
        }

        val finalSelection = matched.distinctBy { it.id }.take(2).ifEmpty {
            listOf(NasaCatalog.variableFor("T2M"), NasaCatalog.variableFor("PRECTOTCORR"))
        }

        val rationale = if (matched.isEmpty()) {
            "Sample placeholder match: your question did not specify a single variable keyword, so Air Temperature (2m) and Precipitation are suggested as a broad starting pair."
        } else {
            "Sample placeholder match based on catalog keywords: ${finalSelection.joinToString(" + ") { "${it.displayName} (${it.unit})" }}."
        }

        return AiVariableSuggestion(
            isPlaceholderMode = true,
            notice = FIREBASE_REQUIRED_NOTICE,
            suggestedVariableIds = finalSelection.map { it.id },
            rationale = rationale,
        )
    }
}
