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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.viewmodel.TradingViewModel

@Composable
fun RiskCalculatorScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val params by viewModel.riskParams.collectAsState()
    val calc by viewModel.riskCalculation.collectAsState()
    val userMsg by viewModel.userMessage.collectAsState()

    var balanceText by remember(params.accountBalance) { mutableStateOf(params.accountBalance.toString()) }
    var entryText by remember(params.entryPrice) { mutableStateOf(params.entryPrice.toString()) }
    var slText by remember(params.stopLossPrice) { mutableStateOf(params.stopLossPrice.toString()) }
    var tpText by remember(params.takeProfit1) { mutableStateOf(params.takeProfit1.toString()) }
    var dailyLossText by remember(params.maxDailyLoss) { mutableStateOf(params.maxDailyLoss.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(horizontal = 14.dp)
    ) {
        // Title Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "RISK MANAGEMENT CALCULATOR",
                    color = GoldPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 0.8.sp
                )
                Text(
                    "Position Sizing for ${params.instrument.assetName} (${params.instrument.displaySymbol})",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = {
                    val bal = balanceText.toDoubleOrNull() ?: params.accountBalance
                    val maxLoss = dailyLossText.toDoubleOrNull() ?: params.maxDailyLoss
                    viewModel.saveRiskProfile(bal, params.riskPercentage, maxLoss)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_risk_settings_button")
            ) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = TerminalBackground, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save Cloud", color = TerminalBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        userMsg?.let { msg ->
            Text(msg, color = BullishGreen, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Calculation Results Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("risk_calculation_results_card"),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(GoldPrimary.copy(0.4f)))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Position Sizing Result", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (calc.isCalculationValid) "Formula Validated" else "Calculation Error",
                                color = if (calc.isCalculationValid) BullishGreen else BearishRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TerminalSurfaceVariant, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Suggested Size", color = TextTertiary, fontSize = 10.sp)
                                Text(
                                    "${"%.2f".format(calc.roundedPositionSize)} Lots/Units",
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column {
                                Text("Planned Risk", color = TextTertiary, fontSize = 10.sp)
                                Text(
                                    "$${"%.2f".format(calc.maxPlannedCashRisk)}",
                                    color = BearishRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column {
                                Text("Est. Profit (TP1)", color = TextTertiary, fontSize = 10.sp)
                                Text(
                                    "$${"%.2f".format(calc.estimatedProfitTp1)}",
                                    color = BullishGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Stop Distance: ${"%.2f".format(calc.stopLossDistance)} (${calc.stopLossTicks.toInt()} ticks)", color = TextSecondary, fontSize = 11.sp)
                            Text("R:R Ratio: ${"%.2f".format(calc.riskToReward1)}", color = BlueAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Spacer(Modifier.height(4.dp))
                        Text("Est. Trading Fees/Slippage: $${"%.2f".format(calc.estimatedTradingCosts)}", color = TextTertiary, fontSize = 10.sp)

                        calc.warningMessage?.let { warn ->
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BearishRed.copy(0.12f), RoundedCornerShape(6.dp))
                                    .border(1.dp, BearishRed.copy(0.5f), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = BearishRed, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(warn, color = BearishRed, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Input Parameters Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Account & Risk Parameters", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Spacer(Modifier.height(10.dp))

                        // Risk % Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Risk Per Trade", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                "${"%.1f".format(params.riskPercentage)}% (Default 0.5%)",
                                color = if (params.riskPercentage > 1.0) BearishRed else GoldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Slider(
                            value = params.riskPercentage.toFloat(),
                            onValueChange = {
                                viewModel.updateRiskParameters(params.copy(riskPercentage = it.toDouble()))
                            },
                            valueRange = 0.1f..3.0f,
                            steps = 28,
                            colors = SliderDefaults.colors(
                                thumbColor = GoldPrimary,
                                activeTrackColor = GoldPrimary,
                                inactiveTrackColor = TerminalBorder
                            ),
                            modifier = Modifier.testTag("risk_percentage_slider")
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = balanceText,
                                onValueChange = {
                                    balanceText = it
                                    it.toDoubleOrNull()?.let { b ->
                                        viewModel.updateRiskParameters(params.copy(accountBalance = b))
                                    }
                                },
                                label = { Text("Account Balance ($)") },
                                modifier = Modifier.weight(1f).testTag("account_balance_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            OutlinedTextField(
                                value = dailyLossText,
                                onValueChange = {
                                    dailyLossText = it
                                    it.toDoubleOrNull()?.let { l ->
                                        viewModel.updateRiskParameters(params.copy(maxDailyLoss = l))
                                    }
                                },
                                label = { Text("Max Daily Loss ($)") },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = entryText,
                                onValueChange = {
                                    entryText = it
                                    it.toDoubleOrNull()?.let { e ->
                                        viewModel.updateRiskParameters(params.copy(entryPrice = e))
                                    }
                                },
                                label = { Text("Entry Price") },
                                modifier = Modifier.weight(1f).testTag("entry_price_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            OutlinedTextField(
                                value = slText,
                                onValueChange = {
                                    slText = it
                                    it.toDoubleOrNull()?.let { s ->
                                        viewModel.updateRiskParameters(params.copy(stopLossPrice = s))
                                    }
                                },
                                label = { Text("Stop Loss") },
                                modifier = Modifier.weight(1f).testTag("stop_loss_input"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = tpText,
                            onValueChange = {
                                tpText = it
                                it.toDoubleOrNull()?.let { t ->
                                    viewModel.updateRiskParameters(params.copy(takeProfit1 = t))
                                }
                            },
                            label = { Text("Take Profit Target") },
                            modifier = Modifier.fillMaxWidth().testTag("take_profit_input"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
