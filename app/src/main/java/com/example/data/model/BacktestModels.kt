package com.example.data.model

data class BacktestTrade(
    val id: String,
    val instrument: Instrument,
    val entryTime: Long,
    val exitTime: Long,
    val direction: SignalType,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val exitPrice: Double,
    val exitReason: String, // "TP1_HIT", "TP2_HIT", "SL_HIT", "TIME_EXIT"
    val profitLossCash: Double,
    val profitLossPercent: Double,
    val riskRewardAchieved: Double,
    val isWin: Boolean,
    val marketRegime: String
)

data class BacktestSummary(
    val instrument: Instrument,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePercent: Double,
    val profitFactor: Double,
    val netPnL: Double,
    val maxDrawdownPercent: Double,
    val averageWin: Double,
    val averageLoss: Double,
    val averageRiskReward: Double,
    val trades: List<BacktestTrade>,
    val testAssumptions: String = "Includes spread, trading fees, and slippage buffer. Closed candle confirmation only, no look-ahead bias."
)
