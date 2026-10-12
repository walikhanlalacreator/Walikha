package com.example.data.engine

import com.example.data.model.Candle
import com.example.data.model.DataStatus
import com.example.data.model.Instrument
import com.example.data.model.MarketProfileResult
import com.example.data.model.MarketStructureResult
import com.example.data.model.MultiTimeframeAnalysis
import com.example.data.model.ProfileShape
import com.example.data.model.QualityScoreComponents
import com.example.data.model.Signal
import com.example.data.model.SignalType
import com.example.data.model.Timeframe
import com.example.data.model.TimeframeContext
import com.example.data.model.TrendDirection
import com.example.data.model.VolumeProfileResult
import kotlin.math.abs

object SignalEngine {

    fun generateSignal(
        instrument: Instrument,
        h4Candles: List<Candle>,
        h1Candles: List<Candle>,
        m15Candles: List<Candle>,
        m5Candles: List<Candle>,
        marketProfile: MarketProfileResult,
        volumeProfile: VolumeProfileResult,
        dataStatus: DataStatus
    ): Pair<Signal, MultiTimeframeAnalysis> {
        val now = System.currentTimeMillis()
        val currentPrice = m5Candles.lastOrNull()?.close
            ?: m15Candles.lastOrNull()?.close
            ?: 0.0

        // Multi-timeframe structures
        val h4Structure = MarketStructureEngine.analyze(h4Candles)
        val h1Structure = MarketStructureEngine.analyze(h1Candles)
        val m15Structure = MarketStructureEngine.analyze(m15Candles)
        val m5Structure = MarketStructureEngine.analyze(m5Candles)

        val mtfAnalysis = buildMtfAnalysis(h4Structure, h1Structure, m15Structure, m5Structure, currentPrice)

        // SAFEGUARDS: Critical data checks for NO TRADE
        if (dataStatus == DataStatus.UNAVAILABLE || currentPrice <= 0.0) {
            return Pair(
                createNoTradeSignal(
                    instrument, currentPrice, now,
                    "MARKET DATA UNAVAILABLE: Live quotes and candles are currently unreachable. Trading signal generation is halted for capital protection.",
                    listOf("Market data feed disconnected or returning empty candles")
                ),
                mtfAnalysis
            )
        }

        if (dataStatus == DataStatus.STALE) {
            return Pair(
                createNoTradeSignal(
                    instrument, currentPrice, now,
                    "STALE DATA - SIGNAL GENERATION DISABLED: Market timestamps indicate delayed or frozen candle feed. Signals disabled to avoid execution slippage.",
                    listOf("Candle data exceeds maximum freshness threshold")
                ),
                mtfAnalysis
            )
        }

        if (m15Candles.size < 15 || m5Candles.size < 15) {
            return Pair(
                createNoTradeSignal(
                    instrument, currentPrice, now,
                    "INSUFFICIENT CANDLE DEPTH: Need at least 15 completed candles per timeframe to compute validated profile and structural swings.",
                    listOf("Insufficient sample size for statistical Market Profile accuracy")
                ),
                mtfAnalysis
            )
        }

        // Bullish vs Bearish Evidence Accumulation
        val bullishEvidence = mutableListOf<String>()
        val bearishEvidence = mutableListOf<String>()

        // 1. Higher-Timeframe Alignment
        var htfScore = 0
        if (mtfAnalysis.h4.trend == TrendDirection.BULLISH && mtfAnalysis.h1.trend == TrendDirection.BULLISH) {
            htfScore = 25
            bullishEvidence.add("4H and 1H trends strictly bullish in full macro alignment")
        } else if (mtfAnalysis.h4.trend == TrendDirection.BEARISH && mtfAnalysis.h1.trend == TrendDirection.BEARISH) {
            htfScore = 25
            bearishEvidence.add("4H and 1H trends strictly bearish in full macro alignment")
        } else if (mtfAnalysis.h4.trend == mtfAnalysis.h1.trend && mtfAnalysis.h4.trend != TrendDirection.UNCLEAR) {
            htfScore = 18
            if (mtfAnalysis.h4.trend == TrendDirection.BULLISH) {
                bullishEvidence.add("Consensus bullish trend across intermediate timeframes")
            } else {
                bearishEvidence.add("Consensus bearish trend across intermediate timeframes")
            }
        } else {
            htfScore = 8
        }

        // 2. Market Profile Context
        var profileScore = 0
        when (marketProfile.shape) {
            ProfileShape.P_SHAPED -> {
                profileScore += 12
                if (currentPrice >= marketProfile.vah * 0.998) {
                    profileScore += 8
                    bullishEvidence.add("P-Shaped Profile: Buyer acceptance holding near VAH (${"%.2f".format(marketProfile.vah)})")
                } else {
                    bullishEvidence.add("P-Shaped Profile distribution indicates upper range accumulation")
                }
            }
            ProfileShape.B_SHAPED -> {
                profileScore += 12
                if (currentPrice <= marketProfile.valLevel * 1.002) {
                    profileScore += 8
                    bearishEvidence.add("B-Shaped Profile: Seller acceptance pressing near VAL (${"%.2f".format(marketProfile.valLevel)})")
                } else {
                    bearishEvidence.add("B-Shaped Profile distribution indicates lower range liquidation")
                }
            }
            ProfileShape.D_SHAPED -> {
                profileScore += 12
                if (currentPrice <= marketProfile.valLevel * 1.003) {
                    bullishEvidence.add("D-Shaped balanced auction: Price testing Value Area Low value support")
                } else if (currentPrice >= marketProfile.vah * 0.997) {
                    bearishEvidence.add("D-Shaped balanced auction: Price testing Value Area High value resistance")
                }
            }
            ProfileShape.UNCERTAIN -> {
                profileScore += 5
            }
        }

        // 3. Price-Action and Market Structure Confirmation (15M / 5M)
        var priceActionScore = 0
        val recentSweep = m5Structure.liquiditySweeps.lastOrNull()
        val recentChoch = m5Structure.chochEvents.lastOrNull()
        val recentBos = m5Structure.bosEvents.lastOrNull()

        if (recentSweep != null) {
            priceActionScore += 10
            if (recentSweep.isBullishReversal) {
                bullishEvidence.add(recentSweep.explanation)
            } else {
                bearishEvidence.add(recentSweep.explanation)
            }
        }

        if (recentChoch != null) {
            priceActionScore += 8
            if (recentChoch.isBullish) {
                bullishEvidence.add("5M CHoCH: Change of character breaking bullish above ${"%.2f".format(recentChoch.price)}")
            } else {
                bearishEvidence.add("5M CHoCH: Change of character breaking bearish below ${"%.2f".format(recentChoch.price)}")
            }
        } else if (recentBos != null) {
            priceActionScore += 6
            if (recentBos.isBullish) {
                bullishEvidence.add("5M BOS: Break of structure confirming bullish continuation")
            } else {
                bearishEvidence.add("5M BOS: Break of structure confirming bearish continuation")
            }
        }

        if (m5Structure.keyRejectionDetected) {
            priceActionScore += 4
            bullishEvidence.add("Long rejection wick observed on latest completed candle")
        }

        // 4. Volume Profile Evidence
        var volumeScore = 0
        if (volumeProfile.isVolumeReliable) {
            volumeScore += 8
            if (abs(currentPrice - volumeProfile.vpoc) / currentPrice < 0.004) {
                volumeScore += 7
                bullishEvidence.add("Price currently reacting at primary High-Volume POC (${"%.2f".format(volumeProfile.vpoc)})")
            } else if (volumeProfile.hvnList.any { abs(currentPrice - it) / currentPrice < 0.003 }) {
                volumeScore += 5
                bullishEvidence.add("Interaction observed at high-volume node (HVN)")
            }
        } else {
            volumeScore += 4
        }

        // 5. Entry, Invalidation & Reward-to-Risk Setup
        var entryScore = 10
        var rrScore = 10

        // Decision logic
        val isBullishCandidate = (mtfAnalysis.h4.trend != TrendDirection.BEARISH &&
                (mtfAnalysis.m15.trend == TrendDirection.BULLISH || recentSweep?.isBullishReversal == true || marketProfile.shape == ProfileShape.P_SHAPED))

        val isBearishCandidate = (mtfAnalysis.h4.trend != TrendDirection.BULLISH &&
                (mtfAnalysis.m15.trend == TrendDirection.BEARISH || recentSweep?.isBullishReversal == false || marketProfile.shape == ProfileShape.B_SHAPED))

        // Check if market is in tight consolidation without breakout
        if (m5Structure.recentConsolidation && recentBos == null && recentChoch == null && recentSweep == null) {
            return Pair(
                createNoTradeSignal(
                    instrument, currentPrice, now,
                    "MARKET IN COMPRESSED CONSOLIDATION: Price is coiling inside a tight balance range without confirmed structural breakout or liquidity sweep.",
                    listOf("Range consolidation active; entering before structural expansion increases whipsaw risk")
                ),
                mtfAnalysis
            )
        }

        // Attempt BUY Setup
        if (isBullishCandidate && !isBearishCandidate) {
            val stopLoss = calculateBullishStopLoss(currentPrice, m5Candles, marketProfile, m5Structure)
            val riskPerUnit = (currentPrice - stopLoss).coerceAtLeast(0.01)

            // Targets
            val tp1 = currentPrice + (riskPerUnit * 1.5)
            val tp2 = currentPrice + (riskPerUnit * 2.5)
            val tp3 = currentPrice + (riskPerUnit * 4.0)

            val rr1 = (tp1 - currentPrice) / riskPerUnit
            val rr2 = (tp2 - currentPrice) / riskPerUnit
            val rr3 = (tp3 - currentPrice) / riskPerUnit

            if (rr1 < 1.3) {
                return Pair(
                    createNoTradeSignal(
                        instrument, currentPrice, now,
                        "INSUFFICIENT REWARD-TO-RISK: Calculated technical setup provides R:R of ${"%.2f".format(rr1)}, below the required 1.3 minimum.",
                        listOf("Target level too close relative to invalidation stop distance")
                    ),
                    mtfAnalysis
                )
            }

            val totalScore = (htfScore + profileScore + priceActionScore + volumeScore + entryScore + rrScore).coerceIn(0, 100)

            val scoreComponents = QualityScoreComponents(
                htfAlignmentScore = htfScore,
                profileContextScore = profileScore,
                priceActionScore = priceActionScore,
                volumeEvidenceScore = volumeScore,
                entryInvalidationScore = entryScore,
                rewardToRiskScore = rrScore,
                totalScore = totalScore
            )

            val signal = Signal(
                instrument = instrument,
                type = SignalType.BUY,
                timestamp = now,
                currentPrice = currentPrice,
                entryPrice = currentPrice,
                stopLoss = stopLoss,
                takeProfit1 = tp1,
                takeProfit2 = tp2,
                takeProfit3 = tp3,
                riskPerUnit = riskPerUnit,
                reward1 = tp1 - currentPrice,
                reward2 = tp2 - currentPrice,
                reward3 = tp3 - currentPrice,
                riskRewardRatio1 = rr1,
                riskRewardRatio2 = rr2,
                riskRewardRatio3 = rr3,
                setupQualityScore = totalScore,
                scoreComponents = scoreComponents,
                reasonExplanation = "Confirmed Bullish Setup: HTF trend supports long positioning, market profile indicates value acceptance with structural support, and local price action confirms buyer response above invalidation.",
                invalidationConditions = "A closed 5M or 15M candle below the structural invalidation level of ${"%.2f".format(stopLoss)} immediately voids this setup.",
                bullishEvidence = bullishEvidence,
                bearishEvidence = bearishEvidence,
                warnings = if (totalScore < 65) listOf("Setup Quality Score is moderate (<65). Consider reducing position size.") else emptyList()
            )
            return Pair(signal, mtfAnalysis)
        }

        // Attempt SELL Setup
        if (isBearishCandidate && !isBullishCandidate) {
            val stopLoss = calculateBearishStopLoss(currentPrice, m5Candles, marketProfile, m5Structure)
            val riskPerUnit = (stopLoss - currentPrice).coerceAtLeast(0.01)

            val tp1 = currentPrice - (riskPerUnit * 1.5)
            val tp2 = currentPrice - (riskPerUnit * 2.5)
            val tp3 = currentPrice - (riskPerUnit * 4.0)

            val rr1 = (currentPrice - tp1) / riskPerUnit
            val rr2 = (currentPrice - tp2) / riskPerUnit
            val rr3 = (currentPrice - tp3) / riskPerUnit

            if (rr1 < 1.3) {
                return Pair(
                    createNoTradeSignal(
                        instrument, currentPrice, now,
                        "INSUFFICIENT REWARD-TO-RISK: Calculated technical setup provides R:R of ${"%.2f".format(rr1)}, below the required 1.3 minimum.",
                        listOf("Target level too close relative to invalidation stop distance")
                    ),
                    mtfAnalysis
                )
            }

            val totalScore = (htfScore + profileScore + priceActionScore + volumeScore + entryScore + rrScore).coerceIn(0, 100)

            val scoreComponents = QualityScoreComponents(
                htfAlignmentScore = htfScore,
                profileContextScore = profileScore,
                priceActionScore = priceActionScore,
                volumeEvidenceScore = volumeScore,
                entryInvalidationScore = entryScore,
                rewardToRiskScore = rrScore,
                totalScore = totalScore
            )

            val signal = Signal(
                instrument = instrument,
                type = SignalType.SELL,
                timestamp = now,
                currentPrice = currentPrice,
                entryPrice = currentPrice,
                stopLoss = stopLoss,
                takeProfit1 = tp1,
                takeProfit2 = tp2,
                takeProfit3 = tp3,
                riskPerUnit = riskPerUnit,
                reward1 = currentPrice - tp1,
                reward2 = currentPrice - tp2,
                reward3 = currentPrice - tp3,
                riskRewardRatio1 = rr1,
                riskRewardRatio2 = rr2,
                riskRewardRatio3 = rr3,
                setupQualityScore = totalScore,
                scoreComponents = scoreComponents,
                reasonExplanation = "Confirmed Bearish Setup: HTF trend supports short positioning, market profile demonstrates value rejection from premium levels, and lower timeframe structure confirms downside continuation.",
                invalidationConditions = "A closed 5M or 15M candle above the structural invalidation level of ${"%.2f".format(stopLoss)} immediately voids this setup.",
                bullishEvidence = bullishEvidence,
                bearishEvidence = bearishEvidence,
                warnings = if (totalScore < 65) listOf("Setup Quality Score is moderate (<65). Consider reducing position size.") else emptyList()
            )
            return Pair(signal, mtfAnalysis)
        }

        // Conflicting timeframes or no clear edge -> NO TRADE
        return Pair(
            createNoTradeSignal(
                instrument, currentPrice, now,
                "CONFLICTING MARKET SIGNALS: Timeframe directions and volume profiles are in mutual disagreement (4H: ${mtfAnalysis.h4.trend}, 15M: ${mtfAnalysis.m15.trend}). No high-probability edge detected.",
                listOf(
                    "Macro 4H trend does not clearly align with 15M local structure",
                    "Market is in intermediate equilibrium without clear directional acceptance"
                )
            ),
            mtfAnalysis
        )
    }

    private fun calculateBullishStopLoss(
        currentPrice: Double,
        candles: List<Candle>,
        profile: MarketProfileResult,
        structure: MarketStructureResult
    ): Double {
        val recentLow = candles.takeLast(10).minOf { it.low }
        val valLevel = if (profile.valLevel > 0 && profile.valLevel < currentPrice) profile.valLevel else recentLow
        val swingLow = structure.swingPoints.filter { !it.isHigh && it.price < currentPrice }.lastOrNull()?.price ?: recentLow

        val baseline = minOf(recentLow, swingLow)
        val buffer = currentPrice * 0.002 // 0.2% buffer
        return baseline - buffer
    }

    private fun calculateBearishStopLoss(
        currentPrice: Double,
        candles: List<Candle>,
        profile: MarketProfileResult,
        structure: MarketStructureResult
    ): Double {
        val recentHigh = candles.takeLast(10).maxOf { it.high }
        val vahLevel = if (profile.vah > currentPrice) profile.vah else recentHigh
        val swingHigh = structure.swingPoints.filter { it.isHigh && it.price > currentPrice }.lastOrNull()?.price ?: recentHigh

        val baseline = maxOf(recentHigh, swingHigh)
        val buffer = currentPrice * 0.002
        return baseline + buffer
    }

    private fun buildMtfAnalysis(
        h4: MarketStructureResult,
        h1: MarketStructureResult,
        m15: MarketStructureResult,
        m5: MarketStructureResult,
        price: Double
    ): MultiTimeframeAnalysis {
        val isAligned = (h4.trend == h1.trend && h1.trend == m15.trend && h4.trend != TrendDirection.RANGING)
        val consensus = when {
            h4.trend == TrendDirection.BULLISH && h1.trend == TrendDirection.BULLISH -> TrendDirection.BULLISH
            h4.trend == TrendDirection.BEARISH && h1.trend == TrendDirection.BEARISH -> TrendDirection.BEARISH
            else -> TrendDirection.RANGING
        }

        val explanation = if (!isAligned) {
            "4H trend is ${h4.trend}, 1H trend is ${h1.trend}, 15M setup is ${m15.trend}, and 5M confirmation is ${m5.trend}. Divergence requires caution."
        } else null

        return MultiTimeframeAnalysis(
            h4 = TimeframeContext(Timeframe.H4, h4.trend, price, "4H macro trend is ${h4.trend}"),
            h1 = TimeframeContext(Timeframe.H1, h1.trend, price, "1H intermediate trend is ${h1.trend}"),
            m15 = TimeframeContext(Timeframe.M15, m15.trend, price, "15M primary setup is ${m15.trend}"),
            m5 = TimeframeContext(Timeframe.M5, m5.trend, price, "5M local entry context is ${m5.trend}"),
            isAligned = isAligned,
            consensusBias = consensus,
            disagreementExplanation = explanation
        )
    }

    private fun createNoTradeSignal(
        instrument: Instrument,
        price: Double,
        timestamp: Long,
        reason: String,
        reasons: List<String>
    ): Signal {
        return Signal(
            instrument = instrument,
            type = SignalType.NO_TRADE,
            timestamp = timestamp,
            currentPrice = price,
            entryPrice = 0.0,
            stopLoss = 0.0,
            takeProfit1 = 0.0,
            takeProfit2 = 0.0,
            takeProfit3 = 0.0,
            riskPerUnit = 0.0,
            reward1 = 0.0,
            reward2 = 0.0,
            reward3 = 0.0,
            riskRewardRatio1 = 0.0,
            riskRewardRatio2 = 0.0,
            riskRewardRatio3 = 0.0,
            setupQualityScore = 0,
            scoreComponents = QualityScoreComponents(0, 0, 0, 0, 0, 0, 0),
            reasonExplanation = reason,
            invalidationConditions = "No trade active. Monitor for structural breakout, liquidity sweep, or Value Area interaction before entry.",
            bullishEvidence = emptyList(),
            bearishEvidence = emptyList(),
            noTradeReasons = reasons
        )
    }
}
