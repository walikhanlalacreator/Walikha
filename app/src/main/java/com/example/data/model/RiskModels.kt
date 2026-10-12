package com.example.data.model

data class RiskParameters(
    val accountBalance: Double = 10000.0,
    val accountCurrency: String = "USD",
    val instrument: Instrument = Instrument.XAU_USD,
    val tradingVenue: String = "Global CFD / Spot",
    val tradingMode: String = "Margin / CFD",
    val riskPercentage: Double = 0.5, // 0.5% default
    val entryPrice: Double = 2650.0,
    val stopLossPrice: Double = 2640.0,
    val takeProfit1: Double = 2665.0,
    val takeProfit2: Double = 2680.0,
    val takeProfit3: Double = 2700.0,
    val contractSize: Double = 100.0,
    val minQuantity: Double = 0.01,
    val quantityStep: Double = 0.01,
    val tickSize: Double = 0.05,
    val tickValue: Double = 5.0,
    val estimatedFeePercent: Double = 0.02,
    val estimatedSlippageTicks: Double = 1.0,
    val maxDailyLoss: Double = 300.0,
    val maxCombinedOpenRisk: Double = 500.0
)

data class RiskCalculationResult(
    val maxPlannedCashRisk: Double,
    val stopLossDistance: Double,
    val stopLossTicks: Double,
    val calculatedPositionSize: Double, // lots or coins
    val roundedPositionSize: Double,
    val estimatedLossAtStopLoss: Double,
    val estimatedProfitTp1: Double,
    val estimatedProfitTp2: Double,
    val estimatedProfitTp3: Double,
    val riskToReward1: Double,
    val riskToReward2: Double,
    val riskToReward3: Double,
    val estimatedTradingCosts: Double,
    val effectiveRiskPercentage: Double,
    val warningMessage: String? = null,
    val isCalculationValid: Boolean = true
)
