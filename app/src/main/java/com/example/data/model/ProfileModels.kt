package com.example.data.model

enum class ProfileShape(val label: String) {
    P_SHAPED("P-Shaped Profile"),
    B_SHAPED("B-Shaped Profile"),
    D_SHAPED("D-Shaped Profile"),
    UNCERTAIN("Profile Classification Uncertain")
}

data class TpoLevel(
    val price: Double,
    val count: Int,
    val isPoc: Boolean = false,
    val isInValueArea: Boolean = false
)

data class MarketProfileResult(
    val poc: Double,
    val vah: Double,
    val valLevel: Double,
    val shape: ProfileShape,
    val shapeExplanation: String,
    val initialBalanceHigh: Double,
    val initialBalanceLow: Double,
    val developingPoc: Double,
    val prevSessionPoc: Double,
    val prevSessionVah: Double,
    val prevSessionVal: Double,
    val currentSessionHigh: Double,
    val currentSessionLow: Double,
    val sessionDescription: String,
    val tpoLevels: List<TpoLevel> = emptyList()
)

data class VolumeNode(
    val price: Double,
    val volume: Double,
    val isPoc: Boolean = false,
    val isInValueArea: Boolean = false,
    val isHvn: Boolean = false,
    val isLvn: Boolean = false
)

data class VolumeZone(
    val lowPrice: Double,
    val highPrice: Double,
    val isAcceptanceZone: Boolean,
    val label: String
)

data class VolumeProfileResult(
    val vpoc: Double,
    val vah: Double,
    val valLevel: Double,
    val hvnList: List<Double>,
    val lvnList: List<Double>,
    val concentrationZones: List<VolumeZone>,
    val volumeNodes: List<VolumeNode>,
    val volumeDataSource: String,
    val isVolumeReliable: Boolean
)

enum class TrendDirection {
    BULLISH,
    BEARISH,
    RANGING,
    UNCLEAR
}

data class SwingPoint(
    val timestamp: Long,
    val price: Double,
    val isHigh: Boolean,
    val label: String // "HH", "HL", "LH", "LL"
)

data class StructureEvent(
    val timestamp: Long,
    val price: Double,
    val type: String, // "BOS" or "CHoCH"
    val isBullish: Boolean,
    val explanation: String
)

data class LiquiditySweep(
    val timestamp: Long,
    val sweptLevel: Double,
    val extremePrice: Double,
    val isBullishReversal: Boolean, // Swept prior low & closed above = bullish
    val explanation: String
)

data class MarketStructureResult(
    val trend: TrendDirection,
    val swingPoints: List<SwingPoint>,
    val bosEvents: List<StructureEvent>,
    val chochEvents: List<StructureEvent>,
    val liquiditySweeps: List<LiquiditySweep>,
    val supportLevels: List<Double>,
    val resistanceLevels: List<Double>,
    val recentConsolidation: Boolean,
    val keyRejectionDetected: Boolean
)

data class TimeframeContext(
    val timeframe: Timeframe,
    val trend: TrendDirection,
    val lastClose: Double,
    val structureSummary: String,
    val keyLevel: Double? = null
)

data class MultiTimeframeAnalysis(
    val h4: TimeframeContext,
    val h1: TimeframeContext,
    val m15: TimeframeContext,
    val m5: TimeframeContext,
    val isAligned: Boolean,
    val consensusBias: TrendDirection,
    val disagreementExplanation: String?
)

enum class SignalType {
    BUY,
    SELL,
    NO_TRADE
}

data class QualityScoreComponents(
    val htfAlignmentScore: Int,      // 0 - 25
    val profileContextScore: Int,    // 0 - 20
    val priceActionScore: Int,       // 0 - 20
    val volumeEvidenceScore: Int,    // 0 - 15
    val entryInvalidationScore: Int, // 0 - 10
    val rewardToRiskScore: Int,      // 0 - 10
    val totalScore: Int              // 0 - 100
)

data class Signal(
    val instrument: Instrument,
    val type: SignalType,
    val timestamp: Long,
    val currentPrice: Double,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val riskPerUnit: Double,
    val reward1: Double,
    val reward2: Double,
    val reward3: Double,
    val riskRewardRatio1: Double,
    val riskRewardRatio2: Double,
    val riskRewardRatio3: Double,
    val setupQualityScore: Int,
    val scoreComponents: QualityScoreComponents,
    val reasonExplanation: String,
    val invalidationConditions: String,
    val bullishEvidence: List<String>,
    val bearishEvidence: List<String>,
    val noTradeReasons: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
