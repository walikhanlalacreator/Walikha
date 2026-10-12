package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Instrument
import com.example.data.model.Timeframe
import com.example.ui.components.CandlestickProfileChart
import com.example.ui.components.MultiTimeframeGrid
import com.example.ui.components.ProfileDistributionCard
import com.example.ui.components.SignalCard
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
import com.example.ui.viewmodel.NavTab
import com.example.ui.viewmodel.TradingViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnalysisDetailScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bundle by viewModel.currentBundle.collectAsState()
    val selectedInstrument by viewModel.selectedInstrument.collectAsState()
    val selectedTf by viewModel.selectedTimeframe.collectAsState()
    val isMarketLoading by viewModel.isMarketLoading.collectAsState()

    var showJournalDialog by remember { mutableStateOf(false) }
    var journalNotes by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(horizontal = 14.dp)
    ) {
        // Instrument selector row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Instrument.entries.forEach { inst ->
                val isSel = (inst == selectedInstrument)
                Box(
                    modifier = Modifier
                        .testTag("instrument_chip_${inst.id}")
                        .background(
                            if (isSel) GoldPrimary else TerminalSurfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, if (isSel) GoldPrimary else TerminalBorder, RoundedCornerShape(8.dp))
                        .clickable { viewModel.selectInstrument(inst) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = inst.displaySymbol,
                        color = if (isSel) TerminalBackground else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Timeframe selector bar + Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Timeframe.entries.forEach { tf ->
                    val isSel = (tf == selectedTf)
                    Box(
                        modifier = Modifier
                            .testTag("timeframe_chip_${tf.label}")
                            .background(
                                if (isSel) BlueAccent.copy(0.2f) else TerminalSurfaceVariant,
                                RoundedCornerShape(6.dp)
                            )
                            .border(1.dp, if (isSel) BlueAccent else TerminalBorder, RoundedCornerShape(6.dp))
                            .clickable { viewModel.setTimeframe(tf) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tf.label,
                            color = if (isSel) BlueAccent else TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row {
                IconButton(
                    onClick = { viewModel.speakSignalSummary(context) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "TTS Voice", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { viewModel.refreshMarkets() },
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isMarketLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GoldPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        if (bundle == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldPrimary)
            }
            return
        }

        val b = bundle!!
        val candles = b.snapshot.candlesByTimeframe[selectedTf] ?: emptyList()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Price & Status Banner
            item {
                val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                val isWs = b.snapshot.isWebSocket
                val ts = b.snapshot.lastUpdateTimestamp

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("terminal_price_banner"),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${selectedInstrument.assetName} (${selectedInstrument.displaySymbol})",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .testTag("terminal_feed_badge")
                                            .background(
                                                if (isWs) BullishGreen.copy(0.18f) else BlueAccent.copy(0.18f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isWs) Icons.Default.Bolt else Icons.Default.CloudSync,
                                                contentDescription = null,
                                                tint = if (isWs) BullishGreen else BlueAccent,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                text = if (isWs) "WS LIVE" else "REST FALLBACK",
                                                color = if (isWs) BullishGreen else BlueAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (b.snapshot.currentPrice > 0) "$${"%.2f".format(b.snapshot.currentPrice)}" else "Syncing...",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 24.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.testTag("terminal_current_price")
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${if (b.snapshot.change24hPercent >= 0) "+" else ""}${"%.2f".format(b.snapshot.change24hPercent)}%",
                                        color = if (b.snapshot.change24hPercent >= 0) BullishGreen else BearishRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Quote: ${selectedInstrument.quoteCurrency}", color = TextTertiary, fontSize = 10.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (ts > 0) "Updated: ${timeFormat.format(Date(ts))}" else "Updated: --",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.testTag("terminal_last_update_timestamp")
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Data Source line
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TerminalSurfaceVariant, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Source: ${b.snapshot.dataProvider}",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                modifier = Modifier.testTag("terminal_data_source")
                            )
                            Text(
                                text = "Spread: $${"%.2f".format(b.snapshot.spread)}",
                                color = TextTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Interactive Candlestick + Market Profile Canvas Chart
            item {
                CandlestickProfileChart(
                    candles = candles,
                    marketProfile = b.marketProfile,
                    volumeProfile = b.volumeProfile
                )
            }

            // Signal Decision & Execution Card
            item {
                SignalCard(
                    signal = b.signal,
                    onSaveToJournal = { showJournalDialog = true }
                )
            }

            // Quick Actions: Journal Trade & AI Deep Analysis
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showJournalDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_journal_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TerminalSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Journal Setup", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            viewModel.setTab(NavTab.AI_INTEL)
                            viewModel.runAiDeepThinking()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_deep_think_cta"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = TerminalBackground, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AI Deep Think", color = TerminalBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Market Profile Distribution Card (P, B, D Shape)
            item {
                ProfileDistributionCard(profile = b.marketProfile)
            }

            // Volume Profile Breakdown Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Volume Profile Order Flow", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                if (b.volumeProfile.isVolumeReliable) "Reliable Traded Vol" else "Volume Limited",
                                color = if (b.volumeProfile.isVolumeReliable) BullishGreen else GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Type: ${b.volumeProfile.volumeDataSource}",
                            color = TextTertiary,
                            fontSize = 11.sp
                        )

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TerminalSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("VPOC", color = TextTertiary, fontSize = 10.sp)
                                Text("$${"%.2f".format(b.volumeProfile.vpoc)}", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Column {
                                Text("High Volume Nodes", color = TextTertiary, fontSize = 10.sp)
                                Text(
                                    b.volumeProfile.hvnList.take(2).joinToString { "$${"%.2f".format(it)}" }.ifEmpty { "None" },
                                    color = BlueAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            Column {
                                Text("Low Volume Nodes", color = TextTertiary, fontSize = 10.sp)
                                Text(
                                    b.volumeProfile.lvnList.take(2).joinToString { "$${"%.2f".format(it)}" }.ifEmpty { "None" },
                                    color = NeutralGray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Multi-Timeframe Matrix
            item {
                MultiTimeframeGrid(mtf = b.mtfAnalysis)
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Journal Entry Dialog
    if (showJournalDialog) {
        AlertDialog(
            onDismissRequest = { showJournalDialog = false },
            title = { Text("Save Signal to Journal", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Logging ${bundle?.instrument?.displaySymbol} ${bundle?.signal?.type} at $${bundle?.signal?.entryPrice}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = journalNotes,
                        onValueChange = { journalNotes = it },
                        label = { Text("Trading Notes & Context") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val isGuest by viewModel.isGuestMode.collectAsState()
                Button(
                    onClick = {
                        viewModel.saveCurrentSignalToJournal(journalNotes)
                        journalNotes = ""
                        showJournalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text(
                        if (isGuest) "Save to Local Journal" else "Save to Cloud",
                        color = TerminalBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showJournalDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = TerminalSurface
        )
    }
}
