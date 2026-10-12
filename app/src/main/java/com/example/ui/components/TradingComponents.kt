package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Candle
import com.example.data.model.MarketProfileResult
import com.example.data.model.MultiTimeframeAnalysis
import com.example.data.model.ProfileShape
import com.example.data.model.Signal
import com.example.data.model.SignalType
import com.example.data.model.TrendDirection
import com.example.data.model.VolumeProfileResult
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

@Composable
fun CandlestickProfileChart(
    candles: List<Candle>,
    marketProfile: MarketProfileResult,
    volumeProfile: VolumeProfileResult,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var selectedCandle by remember { mutableStateOf<Candle?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(TerminalBackground, RoundedCornerShape(12.dp))
            .border(1.dp, TerminalBorder, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        if (candles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Waiting for historical candle data...", color = TextSecondary, fontSize = 12.sp)
            }
            return@Box
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("candlestick_profile_chart")
                .pointerInput(candles) {
                    detectTapGestures { offset ->
                        val chartWidth = size.width * 0.72f
                        val candleWidth = chartWidth / candles.size
                        val idx = (offset.x / candleWidth).toInt().coerceIn(0, candles.size - 1)
                        selectedCandle = candles[idx]
                    }
                }
        ) {
            val chartWidth = size.width * 0.72f
            val profileWidth = size.width * 0.28f

            val minPrice = candles.minOf { it.low }
            val maxPrice = candles.maxOf { it.high }
            val priceRange = (maxPrice - minPrice).coerceAtLeast(0.0001)

            fun priceToY(price: Double): Float {
                return (size.height - ((price - minPrice) / priceRange * size.height)).toFloat()
            }

            // Draw Value Area Background Shading
            if (marketProfile.vah > 0 && marketProfile.valLevel > 0) {
                val vahY = priceToY(marketProfile.vah)
                val valY = priceToY(marketProfile.valLevel)
                drawRect(
                    color = BlueAccent.copy(alpha = 0.07f),
                    topLeft = Offset(0f, minOf(vahY, valY)),
                    size = Size(chartWidth, kotlin.math.abs(valY - vahY))
                )
            }

            // Draw Volume Profile Histogram on right side
            val maxVol = volumeProfile.volumeNodes.maxOfOrNull { it.volume } ?: 1.0
            for (node in volumeProfile.volumeNodes) {
                val y = priceToY(node.price)
                val barLen = ((node.volume / maxVol) * profileWidth * 0.85f).toFloat()
                val barColor = when {
                    node.isPoc -> GoldPrimary
                    node.isInValueArea -> BlueAccent.copy(alpha = 0.7f)
                    node.isHvn -> Color(0xFF60A5FA)
                    else -> NeutralGray.copy(alpha = 0.35f)
                }
                drawRect(
                    color = barColor,
                    topLeft = Offset(chartWidth + (profileWidth - barLen), y - 2f),
                    size = Size(barLen, 3f)
                )
            }

            // Draw POC and VPOC Horizontal lines
            if (marketProfile.poc > 0) {
                val pocY = priceToY(marketProfile.poc)
                drawLine(
                    color = GoldPrimary,
                    start = Offset(0f, pocY),
                    end = Offset(size.width, pocY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                )
            }

            // Draw Candlesticks
            val candleStep = chartWidth / candles.size
            val candleBodyWidth = (candleStep * 0.65f).coerceAtLeast(2f)

            candles.forEachIndexed { i, c ->
                val x = (i + 0.5f) * candleStep
                val openY = priceToY(c.open)
                val closeY = priceToY(c.close)
                val highY = priceToY(c.high)
                val lowY = priceToY(c.low)

                val color = if (c.isBullish) BullishGreen else BearishRed

                // Wick
                drawLine(
                    color = color,
                    start = Offset(x, highY),
                    end = Offset(x, lowY),
                    strokeWidth = 1.5f
                )

                // Body
                val bodyTop = minOf(openY, closeY)
                val bodyHeight = kotlin.math.abs(closeY - openY).coerceAtLeast(1.5f)
                drawRect(
                    color = color,
                    topLeft = Offset(x - candleBodyWidth / 2f, bodyTop),
                    size = Size(candleBodyWidth, bodyHeight)
                )
            }

            // Overlay Level Labels
            drawTextLabel(textMeasurer, "POC ${"%.2f".format(marketProfile.poc)}", Offset(4f, priceToY(marketProfile.poc) - 14f), GoldPrimary)
            if (marketProfile.vah > 0) {
                drawTextLabel(textMeasurer, "VAH ${"%.2f".format(marketProfile.vah)}", Offset(4f, priceToY(marketProfile.vah) - 14f), BlueAccent)
            }
            if (marketProfile.valLevel > 0) {
                drawTextLabel(textMeasurer, "VAL ${"%.2f".format(marketProfile.valLevel)}", Offset(4f, priceToY(marketProfile.valLevel) + 2f), BlueAccent)
            }
        }

        // Inspection tooltip
        selectedCandle?.let { c ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(TerminalSurface.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                    .border(1.dp, TerminalBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "O: ${"%.2f".format(c.open)} H: ${"%.2f".format(c.high)} L: ${"%.2f".format(c.low)} C: ${"%.2f".format(c.close)} Vol: ${c.volume.toInt()}",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun DrawScope.drawTextLabel(measurer: TextMeasurer, text: String, offset: Offset, color: Color) {
    drawText(
        textMeasurer = measurer,
        text = text,
        topLeft = offset,
        style = TextStyle(color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    )
}

@Composable
fun SignalCard(
    signal: Signal,
    onSaveToJournal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (badgeBg, badgeText, badgeColor) = when (signal.type) {
        SignalType.BUY -> Triple(BullishGreen.copy(alpha = 0.18f), "BUY SETUP", BullishGreen)
        SignalType.SELL -> Triple(BearishRed.copy(alpha = 0.18f), "SELL SETUP", BearishRed)
        SignalType.NO_TRADE -> Triple(NeutralGray.copy(alpha = 0.18f), "NO TRADE", NeutralGray)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("signal_card"),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Signal badge + Quality Score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(8.dp))
                        .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 0.8.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Setup Quality: ", color = TextSecondary, fontSize = 11.sp)
                        Text(
                            "${signal.setupQualityScore}/100",
                            color = if (signal.setupQualityScore >= 70) BullishGreen else if (signal.setupQualityScore >= 50) GoldPrimary else NeutralGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    LinearProgressIndicator(
                        progress = { signal.setupQualityScore / 100f },
                        modifier = Modifier
                            .width(90.dp)
                            .height(5.dp),
                        color = if (signal.setupQualityScore >= 70) BullishGreen else GoldPrimary,
                        trackColor = TerminalBorder
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Targets Matrix
            if (signal.type != SignalType.NO_TRADE) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TerminalSurfaceVariant, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LevelItem(title = "Proposed Entry", value = "%.2f".format(signal.entryPrice), color = TextPrimary)
                    LevelItem(title = "Stop Loss", value = "%.2f".format(signal.stopLoss), color = BearishRed)
                    LevelItem(title = "Target 1 (1.5R)", value = "%.2f".format(signal.takeProfit1), color = BullishGreen)
                    LevelItem(title = "Target 2 (2.5R)", value = "%.2f".format(signal.takeProfit2), color = BullishGreen)
                }
                Spacer(Modifier.height(10.dp))
            }

            // Setup explanation
            Text(
                text = signal.reasonExplanation,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Invalidation condition
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = signal.invalidationConditions,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            if (signal.noTradeReasons.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                signal.noTradeReasons.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(r, color = GoldPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LevelItem(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = TextTertiary, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun MultiTimeframeGrid(
    mtf: MultiTimeframeAnalysis,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Multi-Timeframe Alignment", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Box(
                    modifier = Modifier
                        .background(if (mtf.isAligned) BullishGreen.copy(0.15f) else GoldPrimary.copy(0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (mtf.isAligned) "Full Consensus" else "Partial Consensus",
                        color = if (mtf.isAligned) BullishGreen else GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TimeframeItem("4H Trend", mtf.h4.trend)
                TimeframeItem("1H Trend", mtf.h1.trend)
                TimeframeItem("15M Setup", mtf.m15.trend)
                TimeframeItem("5M Entry", mtf.m5.trend)
            }

            mtf.disagreementExplanation?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun TimeframeItem(label: String, trend: TrendDirection) {
    val (icon, color) = when (trend) {
        TrendDirection.BULLISH -> Pair(Icons.Default.ArrowUpward, BullishGreen)
        TrendDirection.BEARISH -> Pair(Icons.Default.ArrowDownward, BearishRed)
        else -> Pair(Icons.Default.CheckCircle, NeutralGray)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(TerminalSurfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = TextTertiary, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text(trend.name.take(4), color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
fun ProfileDistributionCard(
    profile: MarketProfileResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                when (profile.shape) {
                                    ProfileShape.P_SHAPED -> BullishGreen
                                    ProfileShape.B_SHAPED -> BearishRed
                                    ProfileShape.D_SHAPED -> BlueAccent
                                    ProfileShape.UNCERTAIN -> NeutralGray
                                },
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(profile.shape.label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("Value Area 70%", color = TextSecondary, fontSize = 11.sp)
            }

            Spacer(Modifier.height(8.dp))
            Text(profile.shapeExplanation, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LevelItem("POC", "%.2f".format(profile.poc), GoldPrimary)
                LevelItem("VAH (70%)", "%.2f".format(profile.vah), BlueAccent)
                LevelItem("VAL (70%)", "%.2f".format(profile.valLevel), BlueAccent)
                LevelItem("IB High", "%.2f".format(profile.initialBalanceHigh), TextPrimary)
                LevelItem("IB Low", "%.2f".format(profile.initialBalanceLow), TextPrimary)
            }
        }
    }
}
