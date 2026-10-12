package com.example.data.engine

import com.example.data.model.Candle
import com.example.data.model.LiquiditySweep
import com.example.data.model.MarketStructureResult
import com.example.data.model.StructureEvent
import com.example.data.model.SwingPoint
import com.example.data.model.TrendDirection

object MarketStructureEngine {

    /**
     * Identifies market structure, swing points (HH/HL/LH/LL), BOS, CHoCH, and Liquidity Sweeps.
     * Uses closed historical candles to prevent repainting.
     */
    fun analyze(candles: List<Candle>): MarketStructureResult {
        if (candles.size < 7) {
            return MarketStructureResult(
                trend = TrendDirection.UNCLEAR,
                swingPoints = emptyList(),
                bosEvents = emptyList(),
                chochEvents = emptyList(),
                liquiditySweeps = emptyList(),
                supportLevels = emptyList(),
                resistanceLevels = emptyList(),
                recentConsolidation = false,
                keyRejectionDetected = false
            )
        }

        val swingPoints = detectSwingPoints(candles)
        val (bosList, chochList, trend) = evaluateStructureBreaks(candles, swingPoints)
        val sweeps = detectLiquiditySweeps(candles, swingPoints)

        val swingHighPrices = swingPoints.filter { it.isHigh }.map { it.price }
        val swingLowPrices = swingPoints.filter { !it.isHigh }.map { it.price }

        val supportLevels = clusterLevels(swingLowPrices)
        val resistanceLevels = clusterLevels(swingHighPrices)

        // Check recent consolidation: last 5 candles range is tight compared to recent 20
        val recent5 = candles.takeLast(5)
        val recent20 = candles.takeLast(20)
        val recent5Range = recent5.maxOf { it.high } - recent5.minOf { it.low }
        val recent20Range = (recent20.maxOf { it.high } - recent20.minOf { it.low }).coerceAtLeast(0.001)
        val isConsolidating = (recent5Range / recent20Range) < 0.28

        // Check key rejection on the most recent completed candle
        val lastCandle = candles.last()
        val hasRejection = (lastCandle.upperWick > lastCandle.bodySize * 1.5 && lastCandle.upperWick > 0.05) ||
                (lastCandle.lowerWick > lastCandle.bodySize * 1.5 && lastCandle.lowerWick > 0.05)

        return MarketStructureResult(
            trend = trend,
            swingPoints = swingPoints,
            bosEvents = bosList,
            chochEvents = chochList,
            liquiditySweeps = sweeps,
            supportLevels = supportLevels,
            resistanceLevels = resistanceLevels,
            recentConsolidation = isConsolidating,
            keyRejectionDetected = hasRejection
        )
    }

    private fun detectSwingPoints(candles: List<Candle>, window: Int = 2): List<SwingPoint> {
        val points = mutableListOf<SwingPoint>()
        var lastHighPrice: Double? = null
        var lastLowPrice: Double? = null

        for (i in window until candles.size - window) {
            val curr = candles[i]

            // Check if Swing High
            val isHigh = (1..window).all { offset ->
                curr.high >= candles[i - offset].high && curr.high >= candles[i + offset].high
            }

            if (isHigh) {
                val label = when {
                    lastHighPrice == null -> "SH"
                    curr.high > lastHighPrice -> "HH"
                    else -> "LH"
                }
                lastHighPrice = curr.high
                points.add(SwingPoint(curr.timestamp, curr.high, true, label))
            }

            // Check if Swing Low
            val isLow = (1..window).all { offset ->
                curr.low <= candles[i - offset].low && curr.low <= candles[i + offset].low
            }

            if (isLow) {
                val label = when {
                    lastLowPrice == null -> "SL"
                    curr.low > lastLowPrice -> "HL"
                    else -> "LL"
                }
                lastLowPrice = curr.low
                points.add(SwingPoint(curr.timestamp, curr.low, false, label))
            }
        }

        return points
    }

    private fun evaluateStructureBreaks(
        candles: List<Candle>,
        swingPoints: List<SwingPoint>
    ): Triple<List<StructureEvent>, List<StructureEvent>, TrendDirection> {
        val bosList = mutableListOf<StructureEvent>()
        val chochList = mutableListOf<StructureEvent>()
        var currentTrend = TrendDirection.RANGING

        val highs = swingPoints.filter { it.isHigh }
        val lows = swingPoints.filter { !it.isHigh }

        for (candle in candles) {
            // Check break above previous significant high
            val priorHigh = highs.filter { it.timestamp < candle.timestamp }.maxByOrNull { it.timestamp }
            if (priorHigh != null && candle.close > priorHigh.price) {
                if (currentTrend == TrendDirection.BEARISH) {
                    currentTrend = TrendDirection.BULLISH
                    chochList.add(
                        StructureEvent(
                            timestamp = candle.timestamp,
                            price = priorHigh.price,
                            type = "CHoCH",
                            isBullish = true,
                            explanation = "Change of Character: Bullish structural shift breaking above prior lower-high ${priorHigh.price}"
                        )
                    )
                } else {
                    currentTrend = TrendDirection.BULLISH
                    bosList.add(
                        StructureEvent(
                            timestamp = candle.timestamp,
                            price = priorHigh.price,
                            type = "BOS",
                            isBullish = true,
                            explanation = "Break of Structure: Bullish trend continuation breaking above ${priorHigh.price}"
                        )
                    )
                }
            }

            // Check break below previous significant low
            val priorLow = lows.filter { it.timestamp < candle.timestamp }.maxByOrNull { it.timestamp }
            if (priorLow != null && candle.close < priorLow.price) {
                if (currentTrend == TrendDirection.BULLISH) {
                    currentTrend = TrendDirection.BEARISH
                    chochList.add(
                        StructureEvent(
                            timestamp = candle.timestamp,
                            price = priorLow.price,
                            type = "CHoCH",
                            isBullish = false,
                            explanation = "Change of Character: Bearish structural shift breaking below prior higher-low ${priorLow.price}"
                        )
                    )
                } else {
                    currentTrend = TrendDirection.BEARISH
                    bosList.add(
                        StructureEvent(
                            timestamp = candle.timestamp,
                            price = priorLow.price,
                            type = "BOS",
                            isBullish = false,
                            explanation = "Break of Structure: Bearish trend continuation breaking below ${priorLow.price}"
                        )
                    )
                }
            }
        }

        return Triple(bosList, chochList, currentTrend)
    }

    private fun detectLiquiditySweeps(
        candles: List<Candle>,
        swingPoints: List<SwingPoint>
    ): List<LiquiditySweep> {
        val sweeps = mutableListOf<LiquiditySweep>()
        val recentSwings = swingPoints.takeLast(10)

        for (candle in candles.takeLast(20)) {
            for (swing in recentSwings) {
                if (swing.timestamp >= candle.timestamp) continue

                // Bullish Sweep: Candle wick pierces below prior swing low, but candle closes back ABOVE the swing low
                if (!swing.isHigh && candle.low < swing.price && candle.close > swing.price) {
                    val lowerWick = candle.bodyLow - candle.low
                    if (lowerWick > (candle.high - candle.low) * 0.35) { // Meaningful rejection wick
                        sweeps.add(
                            LiquiditySweep(
                                timestamp = candle.timestamp,
                                sweptLevel = swing.price,
                                extremePrice = candle.low,
                                isBullishReversal = true,
                                explanation = "Bullish Liquidity Sweep: Stops hunted below ${"%.2f".format(swing.price)} with instant rejection wick back inside range"
                            )
                        )
                    }
                }

                // Bearish Sweep: Candle wick pierces above prior swing high, but candle closes back BELOW the swing high
                if (swing.isHigh && candle.high > swing.price && candle.close < swing.price) {
                    val upperWick = candle.high - candle.bodyHigh
                    if (upperWick > (candle.high - candle.low) * 0.35) {
                        sweeps.add(
                            LiquiditySweep(
                                timestamp = candle.timestamp,
                                sweptLevel = swing.price,
                                extremePrice = candle.high,
                                isBullishReversal = false,
                                explanation = "Bearish Liquidity Sweep: Buy stops hunted above ${"%.2f".format(swing.price)} with instant rejection wick back inside range"
                            )
                        )
                    }
                }
            }
        }

        return sweeps.distinctBy { it.timestamp }
    }

    private fun clusterLevels(prices: List<Double>, tolerancePercent: Double = 0.003): List<Double> {
        if (prices.isEmpty()) return emptyList()
        val sorted = prices.sorted()
        val clusters = mutableListOf<Double>()
        var currentCluster = mutableListOf<Double>()

        for (p in sorted) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(p)
            } else {
                val avg = currentCluster.average()
                if (kotlin.math.abs(p - avg) / avg <= tolerancePercent) {
                    currentCluster.add(p)
                } else {
                    clusters.add(currentCluster.average())
                    currentCluster = mutableListOf(p)
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
            clusters.add(currentCluster.average())
        }
        return clusters.takeLast(4)
    }
}
