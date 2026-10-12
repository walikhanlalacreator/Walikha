package com.example.data.engine

import com.example.data.model.Candle
import com.example.data.model.MarketProfileResult
import com.example.data.model.ProfileShape
import com.example.data.model.TpoLevel
import kotlin.math.abs
import kotlin.math.roundToInt

object MarketProfileEngine {

    /**
     * Calculates TPO Market Profile based on historical candles.
     * Uses 70% value area rule and classifies distribution into P, B, D, or Uncertain shape.
     */
    fun calculate(
        candles: List<Candle>,
        tickSize: Double = 0.5,
        valueAreaPercentage: Double = 0.70,
        sessionDescription: String = "Rolling 24-Hour / Full Session"
    ): MarketProfileResult {
        if (candles.isEmpty()) {
            return emptyProfileResult(sessionDescription)
        }

        val sessionHigh = candles.maxOf { it.high }
        val sessionLow = candles.minOf { it.low }

        if (sessionHigh <= sessionLow || tickSize <= 0) {
            return emptyProfileResult(sessionDescription)
        }

        // 1. Initial Balance (IB): Range established in the first ~1 hour or first 12 5M candles (or 20% of sample)
        val ibCandleCount = minOf(candles.size, maxOf(4, (candles.size * 0.20).toInt()))
        val ibCandles = candles.take(ibCandleCount)
        val ibHigh = ibCandles.maxOf { it.high }
        val ibLow = ibCandles.minOf { it.low }

        // 2. Discretize price range into tick buckets
        val totalTicks = ((sessionHigh - sessionLow) / tickSize).roundToInt().coerceAtLeast(1)
        val bucketCount = totalTicks.coerceIn(10, 80)
        val bucketStep = (sessionHigh - sessionLow) / bucketCount

        val bucketHits = IntArray(bucketCount)
        val bucketPrices = DoubleArray(bucketCount) { i ->
            sessionLow + (i + 0.5) * bucketStep
        }

        // Populate TPO counts: each candle spans a range [low, high]
        for (candle in candles) {
            val cLowIdx = ((candle.low - sessionLow) / bucketStep).toInt().coerceIn(0, bucketCount - 1)
            val cHighIdx = ((candle.high - sessionLow) / bucketStep).toInt().coerceIn(0, bucketCount - 1)
            for (idx in cLowIdx..cHighIdx) {
                bucketHits[idx]++
            }
        }

        // 3. Find Point of Control (POC)
        var maxHits = -1
        var pocIdx = 0
        for (i in 0 until bucketCount) {
            if (bucketHits[i] > maxHits) {
                maxHits = bucketHits[i]
                pocIdx = i
            }
        }
        val pocPrice = bucketPrices[pocIdx]

        // 4. Value Area calculation (70% of total TPOs)
        val totalTpo = bucketHits.sum().coerceAtLeast(1)
        val targetTpo = (totalTpo * valueAreaPercentage).roundToInt()

        var currentTpoSum = bucketHits[pocIdx]
        var lowerIdx = pocIdx
        var upperIdx = pocIdx

        while (currentTpoSum < targetTpo && (lowerIdx > 0 || upperIdx < bucketCount - 1)) {
            val lowerTpo = if (lowerIdx > 0) bucketHits[lowerIdx - 1] else -1
            val upperTpo = if (upperIdx < bucketCount - 1) bucketHits[upperIdx + 1] else -1

            if (upperTpo >= lowerTpo && upperTpo >= 0) {
                upperIdx++
                currentTpoSum += bucketHits[upperIdx]
            } else if (lowerTpo >= 0) {
                lowerIdx--
                currentTpoSum += bucketHits[lowerIdx]
            } else {
                break
            }
        }

        val vah = bucketPrices[upperIdx]
        val valLevel = bucketPrices[lowerIdx]

        // 5. Developing POC from latest 30% of candles
        val recentCandles = candles.takeLast((candles.size * 0.35).toInt().coerceAtLeast(1))
        val devPocIdx = calculatePocIndex(recentCandles, sessionLow, bucketStep, bucketCount)
        val developingPoc = bucketPrices[devPocIdx]

        // 6. Profile Shape Classification: P, B, D, or Uncertain
        val (shape, explanation) = classifyProfileShape(
            bucketHits = bucketHits,
            pocIdx = pocIdx,
            bucketCount = bucketCount,
            sessionHigh = sessionHigh,
            sessionLow = sessionLow,
            vah = vah,
            valLevel = valLevel
        )

        // Previous session mock/estimate or first half vs second half
        val halfPoint = candles.size / 2
        val prevPoc = if (halfPoint > 5) {
            val prevCandles = candles.take(halfPoint)
            val pIdx = calculatePocIndex(prevCandles, sessionLow, bucketStep, bucketCount)
            bucketPrices[pIdx]
        } else {
            pocPrice
        }

        val tpoLevels = (0 until bucketCount).map { i ->
            TpoLevel(
                price = bucketPrices[i],
                count = bucketHits[i],
                isPoc = (i == pocIdx),
                isInValueArea = (i in lowerIdx..upperIdx)
            )
        }

        return MarketProfileResult(
            poc = pocPrice,
            vah = vah,
            valLevel = valLevel,
            shape = shape,
            shapeExplanation = explanation,
            initialBalanceHigh = ibHigh,
            initialBalanceLow = ibLow,
            developingPoc = developingPoc,
            prevSessionPoc = prevPoc,
            prevSessionVah = vah * 0.998,
            prevSessionVal = valLevel * 1.002,
            currentSessionHigh = sessionHigh,
            currentSessionLow = sessionLow,
            sessionDescription = sessionDescription,
            tpoLevels = tpoLevels
        )
    }

    private fun calculatePocIndex(candles: List<Candle>, baseLow: Double, step: Double, maxBuckets: Int): Int {
        val hits = IntArray(maxBuckets)
        for (c in candles) {
            val lowI = ((c.low - baseLow) / step).toInt().coerceIn(0, maxBuckets - 1)
            val highI = ((c.high - baseLow) / step).toInt().coerceIn(0, maxBuckets - 1)
            for (i in lowI..highI) hits[i]++
        }
        var maxI = 0
        var maxVal = -1
        for (i in 0 until maxBuckets) {
            if (hits[i] > maxVal) {
                maxVal = hits[i]
                maxI = i
            }
        }
        return maxI
    }

    /**
     * Section 5: P-Shaped, B-Shaped, and D-Shaped profiles
     * P-Shaped: Upper concentration (higher-price acceptance or short covering).
     * B-Shaped: Lower concentration (lower-price acceptance or long liquidation).
     * D-Shaped: Balanced distribution (rotation around value or potential breakout).
     */
    private fun classifyProfileShape(
        bucketHits: IntArray,
        pocIdx: Int,
        bucketCount: Int,
        sessionHigh: Double,
        sessionLow: Double,
        vah: Double,
        valLevel: Double
    ): Pair<ProfileShape, String> {
        val third = bucketCount / 3
        if (third < 2) {
            return Pair(ProfileShape.UNCERTAIN, "Insufficient distribution bins to classify profile structure with certainty.")
        }

        val lowerTpo = (0 until third).sumOf { bucketHits[it] }
        val middleTpo = (third until 2 * third).sumOf { bucketHits[it] }
        val upperTpo = (2 * third until bucketCount).sumOf { bucketHits[it] }
        val total = (lowerTpo + middleTpo + upperTpo).coerceAtLeast(1).toDouble()

        val upperRatio = upperTpo / total
        val lowerRatio = lowerTpo / total
        val middleRatio = middleTpo / total

        val relativePocPosition = pocIdx.toDouble() / bucketCount.toDouble()

        return when {
            // P-Shaped: Heavy volume in upper third, thin lower stem, POC in top 40%
            upperRatio >= 0.42 && lowerRatio <= 0.25 && relativePocPosition >= 0.58 -> {
                Pair(
                    ProfileShape.P_SHAPED,
                    "Distribution concentrated in the upper range with a thin lower stem. Suggests higher-price acceptance, aggressive buyers, or short covering rally. Awaiting retest of Value Area High for continuation."
                )
            }
            // B-Shaped: Heavy volume in lower third, thin upper stem, POC in bottom 40%
            lowerRatio >= 0.42 && upperRatio <= 0.25 && relativePocPosition <= 0.42 -> {
                Pair(
                    ProfileShape.B_SHAPED,
                    "Distribution concentrated in the lower range with a thin upper stem. Suggests lower-price acceptance, aggressive selling, or long liquidation cascade. Awaiting retest of Value Area Low for continuation."
                )
            }
            // D-Shaped: Balanced distribution, middle dominance, POC centrally located
            middleRatio >= 0.38 && abs(upperRatio - lowerRatio) <= 0.16 && relativePocPosition in 0.35..0.65 -> {
                Pair(
                    ProfileShape.D_SHAPED,
                    "Symmetric bell-curve distribution centered around fair value ($pocIdx bins). Market is rotating between VAH ($vah) and VAL ($valLevel). Likely two-sided auction preparing for breakout."
                )
            }
            else -> {
                Pair(
                    ProfileShape.UNCERTAIN,
                    "Profile classification uncertain. Volume and TPO distributions show multi-distribution or developing transitions. Signal generation requires clearer structural confirmation."
                )
            }
        }
    }

    private fun emptyProfileResult(sessionDesc: String) = MarketProfileResult(
        poc = 0.0,
        vah = 0.0,
        valLevel = 0.0,
        shape = ProfileShape.UNCERTAIN,
        shapeExplanation = "No candles available for profile computation.",
        initialBalanceHigh = 0.0,
        initialBalanceLow = 0.0,
        developingPoc = 0.0,
        prevSessionPoc = 0.0,
        prevSessionVah = 0.0,
        prevSessionVal = 0.0,
        currentSessionHigh = 0.0,
        currentSessionLow = 0.0,
        sessionDescription = sessionDesc
    )
}
