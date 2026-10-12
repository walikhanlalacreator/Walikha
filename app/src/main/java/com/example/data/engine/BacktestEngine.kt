package com.example.data.engine

import com.example.data.model.BacktestSummary
import com.example.data.model.BacktestTrade
import com.example.data.model.Candle
import com.example.data.model.DataStatus
import com.example.data.model.Instrument
import com.example.data.model.SignalType
import kotlin.math.abs
import kotlin.math.max

object BacktestEngine {

    /**
     * Executes a deterministic backtest on historical candles.
     * Evaluates signals strictly at the close of candle N and enters on candle N+1.
     * Evaluates stop-loss and take-profit hits sequentially candle-by-candle.
     */
    fun runBacktest(
        instrument: Instrument,
        historicalCandles: List<Candle>,
        initialCapital: Double = 10000.0,
        riskPerTradePercent: Double = 0.5
    ): BacktestSummary {
        if (historicalCandles.size < 40) {
            return BacktestSummary(
                instrument = instrument,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRatePercent = 0.0,
                profitFactor = 0.0,
                netPnL = 0.0,
                maxDrawdownPercent = 0.0,
                averageWin = 0.0,
                averageLoss = 0.0,
                averageRiskReward = 0.0,
                trades = emptyList(),
                testAssumptions = "Insufficient historical candles for backtesting (requires at least 40 candles)."
            )
        }

        val trades = mutableListOf<BacktestTrade>()
        var capital = initialCapital
        var peakCapital = initialCapital
        var maxDrawdownCash = 0.0

        val feeRate = instrument.defaultFeeRatePercent / 100.0
        val windowSize = 25
        var inTrade: BacktestTrade? = null

        for (i in windowSize until historicalCandles.size - 1) {
            val evalCandles = historicalCandles.subList(0, i + 1)
            val currentCandle = evalCandles.last()
            val nextCandle = historicalCandles[i + 1]

            // 1. Manage Active Trade
            if (inTrade != null) {
                val trade = inTrade
                var exited = false
                var exitPrice = 0.0
                var exitReason = ""
                var isWin = false

                if (trade.direction == SignalType.BUY) {
                    // Check SL first for conservative execution
                    if (nextCandle.low <= trade.stopLoss) {
                        exitPrice = trade.stopLoss
                        exitReason = "SL_HIT"
                        isWin = false
                        exited = true
                    } else if (nextCandle.high >= trade.takeProfit) {
                        exitPrice = trade.takeProfit
                        exitReason = "TP_HIT"
                        isWin = true
                        exited = true
                    }
                } else if (trade.direction == SignalType.SELL) {
                    if (nextCandle.high >= trade.stopLoss) {
                        exitPrice = trade.stopLoss
                        exitReason = "SL_HIT"
                        isWin = false
                        exited = true
                    } else if (nextCandle.low <= trade.takeProfit) {
                        exitPrice = trade.takeProfit
                        exitReason = "TP_HIT"
                        isWin = true
                        exited = true
                    }
                }

                if (exited) {
                    val slDistance = abs(trade.entryPrice - trade.stopLoss).coerceAtLeast(0.001)
                    val riskCash = capital * (riskPerTradePercent / 100.0)
                    val positionUnits = riskCash / (slDistance * instrument.defaultContractSize)

                    val rawPnl = if (trade.direction == SignalType.BUY) {
                        positionUnits * (exitPrice - trade.entryPrice) * instrument.defaultContractSize
                    } else {
                        positionUnits * (trade.entryPrice - exitPrice) * instrument.defaultContractSize
                    }

                    val feeDeduction = (trade.entryPrice + exitPrice) * positionUnits * instrument.defaultContractSize * feeRate
                    val netPnl = rawPnl - feeDeduction

                    capital += netPnl
                    peakCapital = max(peakCapital, capital)
                    val currentDd = peakCapital - capital
                    if (currentDd > maxDrawdownCash) {
                        maxDrawdownCash = currentDd
                    }

                    val actualRr = if (slDistance > 0) abs(exitPrice - trade.entryPrice) / slDistance else 0.0

                    trades.add(
                        trade.copy(
                            exitTime = nextCandle.timestamp,
                            exitPrice = exitPrice,
                            exitReason = exitReason,
                            profitLossCash = netPnl,
                            profitLossPercent = (netPnl / initialCapital) * 100.0,
                            riskRewardAchieved = actualRr,
                            isWin = isWin
                        )
                    )
                    inTrade = null
                }
                continue
            }

            // 2. Look for new Signal on Closed Candle
            val windowSlice = evalCandles.takeLast(windowSize)
            val profile = MarketProfileEngine.calculate(windowSlice, tickSize = instrument.defaultTickSize)
            val volumeProfile = VolumeProfileEngine.calculate(windowSlice, instrument, tickSize = instrument.defaultTickSize)

            val (signal, _) = SignalEngine.generateSignal(
                instrument = instrument,
                h4Candles = windowSlice,
                h1Candles = windowSlice,
                m15Candles = windowSlice,
                m5Candles = windowSlice,
                marketProfile = profile,
                volumeProfile = volumeProfile,
                dataStatus = DataStatus.LIVE
            )

            if (signal.type == SignalType.BUY || signal.type == SignalType.SELL) {
                // Enter on the open of the next candle
                val entryPrice = nextCandle.open
                val sl = signal.stopLoss
                val tp = signal.takeProfit2 // Use TP2 as standard backtest target

                // Sanity check
                val validBuy = signal.type == SignalType.BUY && sl < entryPrice && tp > entryPrice
                val validSell = signal.type == SignalType.SELL && sl > entryPrice && tp < entryPrice

                if (validBuy || validSell) {
                    inTrade = BacktestTrade(
                        id = "trade_${trades.size + 1}",
                        instrument = instrument,
                        entryTime = nextCandle.timestamp,
                        exitTime = 0L,
                        direction = signal.type,
                        entryPrice = entryPrice,
                        stopLoss = sl,
                        takeProfit = tp,
                        exitPrice = 0.0,
                        exitReason = "OPEN",
                        profitLossCash = 0.0,
                        profitLossPercent = 0.0,
                        riskRewardAchieved = 0.0,
                        isWin = false,
                        marketRegime = profile.shape.label
                    )
                }
            }
        }

        val totalTrades = trades.size
        val winningTrades = trades.count { it.isWin }
        val losingTrades = trades.count { !it.isWin }
        val winRate = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0

        val grossProfit = trades.filter { it.profitLossCash > 0 }.sumOf { it.profitLossCash }
        val grossLoss = abs(trades.filter { it.profitLossCash < 0 }.sumOf { it.profitLossCash })
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 9.99 else 0.0

        val netPnL = trades.sumOf { it.profitLossCash }
        val maxDdPercent = if (peakCapital > 0) (maxDrawdownCash / peakCapital) * 100.0 else 0.0

        val avgWin = if (winningTrades > 0) grossProfit / winningTrades else 0.0
        val avgLoss = if (losingTrades > 0) grossLoss / losingTrades else 0.0
        val avgRr = if (trades.isNotEmpty()) trades.map { it.riskRewardAchieved }.average() else 0.0

        return BacktestSummary(
            instrument = instrument,
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRatePercent = winRate,
            profitFactor = profitFactor,
            netPnL = netPnL,
            maxDrawdownPercent = maxDdPercent,
            averageWin = avgWin,
            averageLoss = avgLoss,
            averageRiskReward = avgRr,
            trades = trades
        )
    }
}
