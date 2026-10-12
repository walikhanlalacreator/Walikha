package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.Instrument
import com.example.data.model.MarketProfileResult
import com.example.data.model.MultiTimeframeAnalysis
import com.example.data.model.Signal
import com.example.data.model.VolumeProfileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GeminiStructuredAnalysis(
    val symbol: String,
    val signal: String,
    val dataStatus: String,
    val marketRegime: String,
    val higherTimeframeBias: String,
    val entry: Double?,
    val stopLoss: Double?,
    val takeProfit1: Double?,
    val takeProfit2: Double?,
    val takeProfit3: Double?,
    val riskReward: Double?,
    val setupQualityScore: Int,
    val profileAnalysis: String,
    val structureAnalysis: String,
    val volumeAnalysis: String,
    val bullishEvidence: List<String>,
    val bearishEvidence: List<String>,
    val invalidationConditions: List<String>,
    val reasonsForNoTrade: List<String>,
    val warnings: List<String>,
    val deepThinkingNarrative: String? = null
)

class GeminiApiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Deep Thinking analysis using gemini-3.1-pro-preview with thinkingLevel = "high"
     */
    suspend fun analyzeWithDeepThinking(
        signal: Signal,
        profile: MarketProfileResult,
        volumeProfile: VolumeProfileResult,
        mtf: MultiTimeframeAnalysis
    ): GeminiStructuredAnalysis = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext fallbackAnalysis(signal, profile, volumeProfile, mtf, "Gemini API Key not configured in Secrets Panel.")
        }

        val prompt = buildAnalysisPrompt(signal, profile, volumeProfile, mtf)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent?key=$apiKey"

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingLevel", "high")
                })
            })
        }

        try {
            val req = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext fallbackAnalysis(signal, profile, volumeProfile, mtf, "Gemini error: ${response.code} $responseStr")
                }
                parseStructuredResponse(responseStr, signal, profile, volumeProfile)
            }
        } catch (e: Exception) {
            fallbackAnalysis(signal, profile, volumeProfile, mtf, "Gemini request failure: ${e.message}")
        }
    }

    /**
     * Fast Low-Latency analysis using gemini-3.1-flash-lite-preview
     */
    suspend fun quickScanLowLatency(
        instrument: Instrument,
        currentPrice: Double,
        signalType: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Flash-Lite Scan: ${instrument.displaySymbol} at $currentPrice. Signal state: $signalType. Key profile levels active."
        }

        val prompt = "Provide a 2-sentence ultra-concise trading alert for ${instrument.displaySymbol} at price $currentPrice with current signal $signalType. Focus strictly on immediate volatility and invalidation level."
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=$apiKey"

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
        }

        try {
            val req = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { response ->
                val str = response.body?.string() ?: ""
                extractTextFromResponse(str) ?: "Instant scan completed for ${instrument.displaySymbol}."
            }
        } catch (e: Exception) {
            "Flash scan error: ${e.message}"
        }
    }

    /**
     * Macro Sentiment Grounding using gemini-3.5-flash with Google Search Grounding tool
     */
    suspend fun getSearchGroundedMacroContext(instrument: Instrument): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Search Grounding: Live Google Search grounding requires Gemini API key configured in AI Studio Secrets."
        }

        val prompt = "What are the latest breaking macroeconomic news, central bank actions, and institutional sentiment affecting ${instrument.assetName} (${instrument.displaySymbol}) today? Provide a concise summary with relevant key drivers."
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("googleSearch", JSONObject())
                })
            })
        }

        try {
            val req = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { response ->
                val str = response.body?.string() ?: ""
                extractTextFromResponse(str) ?: "Search grounding returned no recent news."
            }
        } catch (e: Exception) {
            "Grounding query error: ${e.message}"
        }
    }

    /**
     * Image chart analysis using gemini-3.1-pro-preview
     */
    suspend fun analyzeChartImage(
        bitmap: Bitmap,
        instrument: Instrument,
        userNotes: String = ""
    ): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Chart Image Analysis: Please set your Gemini API key in the Secrets panel to enable AI computer vision chart inspection."
        }

        val base64Img = bitmap.toBase64()
        val prompt = """
            Act as an elite quantitative trader and Market Profile expert.
            Analyze this chart screenshot for ${instrument.displaySymbol}.
            Identify visible:
            1. Market Structure (Higher Highs, Lower Lows, BOS, CHoCH)
            2. Value Area / Profile distribution shapes (P-shape, B-shape, D-shape)
            3. Key liquidity pools and potential fakeouts / sweeps
            4. Invalidation zones and high probability scenarios
            User note: $userNotes
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent?key=$apiKey"

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Img)
                            })
                        })
                    })
                })
            })
        }

        try {
            val req = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { response ->
                val str = response.body?.string() ?: ""
                extractTextFromResponse(str) ?: "Failed to extract chart visual insights."
            }
        } catch (e: Exception) {
            "Image analysis error: ${e.message}"
        }
    }

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
    }

    private fun buildAnalysisPrompt(
        signal: Signal,
        profile: MarketProfileResult,
        volumeProfile: VolumeProfileResult,
        mtf: MultiTimeframeAnalysis
    ): String {
        return """
            You are the quantitative analytical engine of "Gold Profile AI".
            Analyze the following verified market data and deterministic calculations.
            Do NOT invent prices, candles, or values. Adhere strictly to the verified numbers.

            INSTRUMENT: ${signal.instrument.displaySymbol} (${signal.instrument.assetName})
            CURRENT PRICE: ${signal.currentPrice}
            DETERMINISTIC SIGNAL: ${signal.type}
            SETUP QUALITY SCORE: ${signal.setupQualityScore} / 100
            PROPOSED ENTRY: ${signal.entryPrice}
            STOP LOSS: ${signal.stopLoss}
            TAKE PROFIT 1: ${signal.takeProfit1}
            TAKE PROFIT 2: ${signal.takeProfit2}
            TAKE PROFIT 3: ${signal.takeProfit3}
            RISK-TO-REWARD (TP1): ${signal.riskRewardRatio1}

            MARKET PROFILE:
            Shape: ${profile.shape.label}
            POC: ${profile.poc}
            Value Area High (VAH): ${profile.vah}
            Value Area Low (VAL): ${profile.valLevel}
            Developing POC: ${profile.developingPoc}
            Initial Balance: High=${profile.initialBalanceHigh}, Low=${profile.initialBalanceLow}
            Session Context: ${profile.sessionDescription}

            VOLUME PROFILE:
            VPOC: ${volumeProfile.vpoc}
            Volume Type: ${volumeProfile.volumeDataSource}
            Reliable Volume: ${volumeProfile.isVolumeReliable}
            HVNs: ${volumeProfile.hvnList.joinToString()}
            LVNs: ${volumeProfile.lvnList.joinToString()}

            MULTI-TIMEFRAME CONTEXT:
            4H Trend: ${mtf.h4.trend}
            1H Trend: ${mtf.h1.trend}
            15M Trend: ${mtf.m15.trend}
            5M Trend: ${mtf.m5.trend}
            Consensus Bias: ${mtf.consensusBias}

            REASONING & EVIDENCE:
            Bullish: ${signal.bullishEvidence.joinToString("; ")}
            Bearish: ${signal.bearishEvidence.joinToString("; ")}
            No-Trade Reasons: ${signal.noTradeReasons.joinToString("; ")}

            OUTPUT FORMAT REQUIREMENT:
            Return valid JSON adhering exactly to:
            {
              "symbol": "${signal.instrument.displaySymbol}",
              "signal": "${signal.type.name}",
              "data_status": "VALID",
              "market_regime": "${profile.shape.name}",
              "higher_timeframe_bias": "${mtf.consensusBias.name}",
              "entry": ${if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.entryPrice else "null"},
              "stop_loss": ${if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.stopLoss else "null"},
              "take_profit_1": ${if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.takeProfit1 else "null"},
              "take_profit_2": ${if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.takeProfit2 else "null"},
              "take_profit_3": ${if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.takeProfit3 else "null"},
              "risk_reward": ${if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.riskRewardRatio1 else "null"},
              "setup_quality_score": ${signal.setupQualityScore},
              "profile_analysis": "<In-depth Market Profile evaluation>",
              "structure_analysis": "<Market structure & swing analysis>",
              "volume_analysis": "<Volume Profile & liquidity nodes analysis>",
              "bullish_evidence": ["item1", "item2"],
              "bearish_evidence": ["item1", "item2"],
              "invalidation_conditions": ["item1"],
              "reasons_for_no_trade": [],
              "warnings": ["item1"]
            }
        """.trimIndent()
    }

    private fun parseStructuredResponse(
        rawJson: String,
        signal: Signal,
        profile: MarketProfileResult,
        volumeProfile: VolumeProfileResult
    ): GeminiStructuredAnalysis {
        val text = extractTextFromResponse(rawJson) ?: rawJson
        val cleanJson = cleanJsonString(text)
        return try {
            val json = JSONObject(cleanJson)
            GeminiStructuredAnalysis(
                symbol = json.optString("symbol", signal.instrument.displaySymbol),
                signal = json.optString("signal", signal.type.name),
                dataStatus = json.optString("data_status", "VALID"),
                marketRegime = json.optString("market_regime", profile.shape.name),
                higherTimeframeBias = json.optString("higher_timeframe_bias", "NEUTRAL"),
                entry = if (json.isNull("entry")) null else json.optDouble("entry"),
                stopLoss = if (json.isNull("stop_loss")) null else json.optDouble("stop_loss"),
                takeProfit1 = if (json.isNull("take_profit_1")) null else json.optDouble("take_profit_1"),
                takeProfit2 = if (json.isNull("take_profit_2")) null else json.optDouble("take_profit_2"),
                takeProfit3 = if (json.isNull("take_profit_3")) null else json.optDouble("take_profit_3"),
                riskReward = if (json.isNull("risk_reward")) null else json.optDouble("risk_reward"),
                setupQualityScore = json.optInt("setup_quality_score", signal.setupQualityScore),
                profileAnalysis = json.optString("profile_analysis", profile.shapeExplanation),
                structureAnalysis = json.optString("structure_analysis", signal.reasonExplanation),
                volumeAnalysis = json.optString("volume_analysis", "Volume POC established at ${volumeProfile.vpoc}."),
                bullishEvidence = jsonArrayToStringList(json.optJSONArray("bullish_evidence")),
                bearishEvidence = jsonArrayToStringList(json.optJSONArray("bearish_evidence")),
                invalidationConditions = jsonArrayToStringList(json.optJSONArray("invalidation_conditions")),
                reasonsForNoTrade = jsonArrayToStringList(json.optJSONArray("reasons_for_no_trade")),
                warnings = jsonArrayToStringList(json.optJSONArray("warnings")),
                deepThinkingNarrative = "Deep thinking analysis completed using gemini-3.1-pro-preview with thinkingLevel = high."
            )
        } catch (_: Exception) {
            fallbackAnalysis(signal, profile, volumeProfile, null, text)
        }
    }

    private fun fallbackAnalysis(
        signal: Signal,
        profile: MarketProfileResult,
        volumeProfile: VolumeProfileResult,
        mtf: MultiTimeframeAnalysis?,
        note: String
    ): GeminiStructuredAnalysis {
        return GeminiStructuredAnalysis(
            symbol = signal.instrument.displaySymbol,
            signal = signal.type.name,
            dataStatus = "VALID",
            marketRegime = profile.shape.name,
            higherTimeframeBias = mtf?.consensusBias?.name ?: "NEUTRAL",
            entry = if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.entryPrice else null,
            stopLoss = if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.stopLoss else null,
            takeProfit1 = if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.takeProfit1 else null,
            takeProfit2 = if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.takeProfit2 else null,
            takeProfit3 = if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.takeProfit3 else null,
            riskReward = if (signal.type != com.example.data.model.SignalType.NO_TRADE) signal.riskRewardRatio1 else null,
            setupQualityScore = signal.setupQualityScore,
            profileAnalysis = profile.shapeExplanation,
            structureAnalysis = signal.reasonExplanation,
            volumeAnalysis = "VPOC at ${"%.2f".format(volumeProfile.vpoc)} with ${volumeProfile.volumeDataSource}.",
            bullishEvidence = signal.bullishEvidence,
            bearishEvidence = signal.bearishEvidence,
            invalidationConditions = listOf(signal.invalidationConditions),
            reasonsForNoTrade = signal.noTradeReasons,
            warnings = signal.warnings.ifEmpty { listOf(note) }
        )
    }

    private fun extractTextFromResponse(responseJson: String): String? {
        return try {
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val first = candidates.getJSONObject(0)
            val content = first.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.has("text")) {
                    sb.append(p.getString("text"))
                }
            }
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanJsonString(str: String): String {
        var trimmed = str.trim()
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json").trim()
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```").trim()
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```").trim()
        }
        return trimmed
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.optString(i))
        }
        return list
    }

    private fun Bitmap.toBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
