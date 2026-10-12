package com.example.data.engine

import com.example.data.model.Candle
import com.example.data.model.Instrument
import com.example.data.model.VolumeNode
import com.example.data.model.VolumeProfileResult
import com.example.data.model.VolumeZone
import kotlin.math.roundToInt

object VolumeProfileEngine {

    /**
     * Calculates Volume Profile from genuine candle volume.
     * Identifies VPOC, Value Area (VAH, VAL), HVNs (High Volume Nodes), and LVNs (Low Volume Nodes).
     */
    fun calculate(
        candles: List<Candle>,
        instrument: Instrument,
        tickSize: Double = 0.5,
        valueAreaPercentage: Double = 0.70
    ): VolumeProfileResult {
        val volumeSource = instrument.volumeDescription

        if (candles.isEmpty()) {
            return emptyResult(volumeSource, false)
        }

        val totalVolume = candles.sumOf { it.volume }
        val isReliable = totalVolume > 0 && candles.size >= 10

        val maxHigh = candles.maxOf { it.high }
        val minLow = candles.minOf { it.low }

        if (maxHigh <= minLow) {
            return emptyResult(volumeSource, false)
        }

        val bucketCount = 50
        val bucketStep = (maxHigh - minLow) / bucketCount

        val bucketVolumes = DoubleArray(bucketCount)
        val bucketPrices = DoubleArray(bucketCount) { i ->
            minLow + (i + 0.5) * bucketStep
        }

        for (candle in candles) {
            val range = (candle.high - candle.low).coerceAtLeast(0.0001)
            val lowIdx = ((candle.low - minLow) / bucketStep).toInt().coerceIn(0, bucketCount - 1)
            val highIdx = ((candle.high - minLow) / bucketStep).toInt().coerceIn(0, bucketCount - 1)
            val spreadSpan = (highIdx - lowIdx + 1).coerceAtLeast(1)
            val volumePerBucket = candle.volume / spreadSpan

            for (idx in lowIdx..highIdx) {
                bucketVolumes[idx] += volumePerBucket
            }
        }

        // Find VPOC
        var maxVol = -1.0
        var vpocIdx = 0
        for (i in 0 until bucketCount) {
            if (bucketVolumes[i] > maxVol) {
                maxVol = bucketVolumes[i]
                vpocIdx = i
            }
        }
        val vpocPrice = bucketPrices[vpocIdx]

        // Value Area 70% calculation
        val sumVolume = bucketVolumes.sum().coerceAtLeast(0.0001)
        val targetVaVolume = sumVolume * valueAreaPercentage

        var currentSum = bucketVolumes[vpocIdx]
        var lowerIdx = vpocIdx
        var upperIdx = vpocIdx

        while (currentSum < targetVaVolume && (lowerIdx > 0 || upperIdx < bucketCount - 1)) {
            val lowerV = if (lowerIdx > 0) bucketVolumes[lowerIdx - 1] else -1.0
            val upperV = if (upperIdx < bucketCount - 1) bucketVolumes[upperIdx + 1] else -1.0

            if (upperV >= lowerV && upperV >= 0) {
                upperIdx++
                currentSum += bucketVolumes[upperIdx]
            } else if (lowerV >= 0) {
                lowerIdx--
                currentSum += bucketVolumes[lowerIdx]
            } else {
                break
            }
        }

        val vah = bucketPrices[upperIdx]
        val valLevel = bucketPrices[lowerIdx]

        // Detect High Volume Nodes (HVNs) and Low Volume Nodes (LVNs)
        val avgVolume = sumVolume / bucketCount
        val hvnList = mutableListOf<Double>()
        val lvnList = mutableListOf<Double>()

        for (i in 1 until bucketCount - 1) {
            val prev = bucketVolumes[i - 1]
            val curr = bucketVolumes[i]
            val next = bucketVolumes[i + 1]

            // HVN: local maximum significantly above average
            if (curr > prev && curr > next && curr >= avgVolume * 1.3) {
                hvnList.add(bucketPrices[i])
            }
            // LVN: local minimum significantly below average
            if (curr < prev && curr < next && curr <= avgVolume * 0.6) {
                lvnList.add(bucketPrices[i])
            }
        }

        val concentrationZones = mutableListOf<VolumeZone>()
        // Fair Value Zone around VPOC
        val vpocLower = bucketPrices[maxOf(0, vpocIdx - 2)]
        val vpocUpper = bucketPrices[minOf(bucketCount - 1, vpocIdx + 2)]
        concentrationZones.add(
            VolumeZone(
                lowPrice = vpocLower,
                highPrice = vpocUpper,
                isAcceptanceZone = true,
                label = "Primary Fair Value Acceptance Zone (VPOC ${"%.2f".format(vpocPrice)})"
            )
        )

        // Low volume rejection zones near extremities
        if (lvnList.isNotEmpty()) {
            concentrationZones.add(
                VolumeZone(
                    lowPrice = lvnList.minOrNull() ?: minLow,
                    highPrice = (lvnList.minOrNull() ?: minLow) + bucketStep * 2,
                    isAcceptanceZone = false,
                    label = "Price Rejection / Low Liquidity Zone (LVN)"
                )
            )
        }

        val volumeNodes = (0 until bucketCount).map { i ->
            VolumeNode(
                price = bucketPrices[i],
                volume = bucketVolumes[i],
                isPoc = (i == vpocIdx),
                isInValueArea = (i in lowerIdx..upperIdx),
                isHvn = hvnList.contains(bucketPrices[i]),
                isLvn = lvnList.contains(bucketPrices[i])
            )
        }

        return VolumeProfileResult(
            vpoc = vpocPrice,
            vah = vah,
            valLevel = valLevel,
            hvnList = hvnList,
            lvnList = lvnList,
            concentrationZones = concentrationZones,
            volumeNodes = volumeNodes,
            volumeDataSource = volumeSource,
            isVolumeReliable = isReliable
        )
    }

    private fun emptyResult(source: String, reliable: Boolean) = VolumeProfileResult(
        vpoc = 0.0,
        vah = 0.0,
        valLevel = 0.0,
        hvnList = emptyList(),
        lvnList = emptyList(),
        concentrationZones = emptyList(),
        volumeNodes = emptyList(),
        volumeDataSource = source,
        isVolumeReliable = reliable
    )
}
