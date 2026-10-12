package com.example.data.engine

import com.example.data.model.RiskCalculationResult
import com.example.data.model.RiskParameters
import kotlin.math.abs
import kotlin.math.floor

object RiskCalculatorEngine {

    fun calculate(params: RiskParameters): RiskCalculationResult {
        val plannedCashRisk = params.accountBalance * (params.riskPercentage / 100.0)
        val slDistance = abs(params.entryPrice - params.stopLossPrice)

        if (slDistance <= 0.0 || params.contractSize <= 0.0) {
            return RiskCalculationResult(
                maxPlannedCashRisk = plannedCashRisk,
                stopLossDistance = 0.0,
                stopLossTicks = 0.0,
                calculatedPositionSize = 0.0,
                roundedPositionSize = 0.0,
                estimatedLossAtStopLoss = 0.0,
                estimatedProfitTp1 = 0.0,
                estimatedProfitTp2 = 0.0,
                estimatedProfitTp3 = 0.0,
                riskToReward1 = 0.0,
                riskToReward2 = 0.0,
                riskToReward3 = 0.0,
                estimatedTradingCosts = 0.0,
                effectiveRiskPercentage = 0.0,
                warningMessage = "Invalid Stop Loss level: Stop loss distance must be greater than zero.",
                isCalculationValid = false
            )
        }

        val slTicks = if (params.tickSize > 0) slDistance / params.tickSize else 0.0

        // Position sizing: CashRisk / (distance * contractSize)
        val rawPositionSize = plannedCashRisk / (slDistance * params.contractSize)

        // Step rounding
        val step = params.quantityStep.coerceAtLeast(0.0001)
        var roundedSize = floor(rawPositionSize / step) * step
        if (roundedSize < params.minQuantity) {
            roundedSize = params.minQuantity
        }

        val actualLossAtSl = (roundedSize * slDistance * params.contractSize)
        val feeRate = params.estimatedFeePercent / 100.0
        val notionalValue = roundedSize * params.entryPrice * params.contractSize
        val estTradingCosts = notionalValue * feeRate * 2.0 // Round trip entry + exit

        val reward1 = abs(params.takeProfit1 - params.entryPrice)
        val reward2 = abs(params.takeProfit2 - params.entryPrice)
        val reward3 = abs(params.takeProfit3 - params.entryPrice)

        val profitTp1 = roundedSize * reward1 * params.contractSize - estTradingCosts
        val profitTp2 = roundedSize * reward2 * params.contractSize - estTradingCosts
        val profitTp3 = roundedSize * reward3 * params.contractSize - estTradingCosts

        val rr1 = if (slDistance > 0) reward1 / slDistance else 0.0
        val rr2 = if (slDistance > 0) reward2 / slDistance else 0.0
        val rr3 = if (slDistance > 0) reward3 / slDistance else 0.0

        val effectiveRiskPct = if (params.accountBalance > 0) {
            (actualLossAtSl + estTradingCosts) / params.accountBalance * 100.0
        } else {
            0.0
        }

        val warning = when {
            params.riskPercentage > 1.0 -> {
                "High Risk Warning: Risk is set to ${"%.2f".format(params.riskPercentage)}% (exceeds recommended 1.0% professional threshold)."
            }
            actualLossAtSl > params.maxDailyLoss -> {
                "Daily Loss Breach Warning: Planned risk ($${"%.2f".format(actualLossAtSl)}) exceeds your maximum daily loss limit ($${"%.2f".format(params.maxDailyLoss)})."
            }
            actualLossAtSl > params.maxCombinedOpenRisk -> {
                "Open Risk Warning: Planned risk exceeds max combined open portfolio risk limit ($${"%.2f".format(params.maxCombinedOpenRisk)})."
            }
            else -> null
        }

        return RiskCalculationResult(
            maxPlannedCashRisk = plannedCashRisk,
            stopLossDistance = slDistance,
            stopLossTicks = slTicks,
            calculatedPositionSize = rawPositionSize,
            roundedPositionSize = roundedSize,
            estimatedLossAtStopLoss = actualLossAtSl + estTradingCosts,
            estimatedProfitTp1 = profitTp1,
            estimatedProfitTp2 = profitTp2,
            estimatedProfitTp3 = profitTp3,
            riskToReward1 = rr1,
            riskToReward2 = rr2,
            riskToReward3 = rr3,
            estimatedTradingCosts = estTradingCosts,
            effectiveRiskPercentage = effectiveRiskPct,
            warningMessage = warning,
            isCalculationValid = true
        )
    }
}
