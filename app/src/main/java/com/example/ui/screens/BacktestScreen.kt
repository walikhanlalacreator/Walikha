package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NeutralGray
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.TradingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BacktestScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val bundle by viewModel.currentBundle.collectAsState()
    val selectedInstrument by viewModel.selectedInstrument.collectAsState()
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(horizontal = 14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "HISTORICAL BACKTESTING",
                    color = GoldPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 0.8.sp
                )
                Text(
                    "Deterministic Simulation for ${selectedInstrument.assetName}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { viewModel.runBacktest() },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("run_backtest_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TerminalBackground, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Run Test", color = TerminalBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        val summary = bundle?.backtestSummary

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Metrics KPI Dashboard Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("backtest_kpi_card"),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Performance Statistics", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(10.dp))

                        if (summary != null && summary.totalTrades > 0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                KpiItem("Total Trades", "${summary.totalTrades}", TextPrimary)
                                KpiItem(
                                    "Win Rate",
                                    "${"%.1f".format(summary.winRatePercent)}%",
                                    if (summary.winRatePercent >= 50) BullishGreen else BearishRed
                                )
                                KpiItem("Profit Factor", "%.2f".format(summary.profitFactor), GoldPrimary)
                                KpiItem(
                                    "Net PnL",
                                    "${if (summary.netPnL >= 0) "+" else ""}$${"%.2f".format(summary.netPnL)}",
                                    if (summary.netPnL >= 0) BullishGreen else BearishRed
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                KpiItem("Max Drawdown", "-${"%.1f".format(summary.maxDrawdownPercent)}%", BearishRed)
                                KpiItem("Average Win", "+$${"%.2f".format(summary.averageWin)}", BullishGreen)
                                KpiItem("Average Loss", "-$${"%.2f".format(summary.averageLoss)}", BearishRed)
                                KpiItem("Avg R:R", "${"%.2f".format(summary.averageRiskReward)}R", BlueAccent)
                            }
                        } else {
                            Text(
                                "Tap 'Run Test' to execute the deterministic backtester across the loaded historical candles. Uses closed candle verification with spread and fee deduction.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        summary?.testAssumptions?.let {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(it, color = TextTertiary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Trade Log Table
            if (summary != null && summary.trades.isNotEmpty()) {
                item {
                    Text(
                        "EXECUTED TRADE LOG (${summary.trades.size})",
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp
                    )
                }

                items(summary.trades.reversed()) { trade ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                        shape = RoundedCornerShape(10.dp),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (trade.direction == com.example.data.model.SignalType.BUY) BullishGreen.copy(0.18f) else BearishRed.copy(0.18f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            trade.direction.name,
                                            color = if (trade.direction == com.example.data.model.SignalType.BUY) BullishGreen else BearishRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        dateFormat.format(Date(trade.entryTime)),
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Entry: ${"%.2f".format(trade.entryPrice)} • Exit: ${"%.2f".format(trade.exitPrice)} (${trade.exitReason})",
                                    color = TextTertiary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (trade.profitLossCash >= 0) "+" else ""}$${"%.2f".format(trade.profitLossCash)}",
                                    color = if (trade.isWin) BullishGreen else BearishRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${"%.2f".format(trade.riskRewardAchieved)}R",
                                    color = BlueAccent,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun KpiItem(title: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = TextTertiary, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}
