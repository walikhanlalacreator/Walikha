package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SignalRecord
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
fun JournalScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val signals by viewModel.savedSignals.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var editingSignal by remember { mutableStateOf<SignalRecord?>(null) }
    var selectedOutcome by remember { mutableStateOf("TP1_HIT") }
    var editNotes by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(horizontal = 14.dp)
    ) {
        val isGuest by viewModel.isGuestMode.collectAsState()

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Book, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "TRADING JOURNAL",
                        color = GoldPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    if (isGuest) "Local Session Journal (${signals.size} Signals Logged • Offline)"
                    else "Cloud-Synced Execution History (${signals.size} Signals Logged)",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        if (isGuest) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .background(GoldPrimary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Guest Mode: Signals are recorded in your local session. To enable permanent multi-device cloud synchronization, connect your Google account from the terminal header.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        if (signals.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No journaled signals yet.", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isGuest) "On the Analysis screen, tap 'Journal Setup' to log setups with live parameters into your local journal."
                        else "On the Analysis screen, tap 'Journal Setup' to log setups with live parameters into Firestore.",
                        color = TextTertiary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(signals) { sig ->
                val isBuy = sig.direction == "BUY"
                val outcomeColor = when (sig.outcome) {
                    "TP1_HIT", "TP2_HIT", "TP3_HIT" -> BullishGreen
                    "SL_HIT" -> BearishRed
                    "INVALIDATED" -> GoldPrimary
                    else -> BlueAccent
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("journal_signal_${sig.id}"),
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
                                        .background(if (isBuy) BullishGreen.copy(0.18f) else BearishRed.copy(0.18f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        sig.direction,
                                        color = if (isBuy) BullishGreen else BearishRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(sig.symbol, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(outcomeColor.copy(0.15f), RoundedCornerShape(6.dp))
                                        .border(1.dp, outcomeColor.copy(0.4f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(sig.outcome, color = outcomeColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }

                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        editingSignal = sig
                                        selectedOutcome = sig.outcome
                                        editNotes = sig.journalNotes
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Outcome", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TerminalSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Entry", color = TextTertiary, fontSize = 9.sp)
                                Text("$${"%.2f".format(sig.entry)}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                            Column {
                                Text("Stop Loss", color = TextTertiary, fontSize = 9.sp)
                                Text("$${"%.2f".format(sig.stopLoss)}", color = BearishRed, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                            Column {
                                Text("Target 1", color = TextTertiary, fontSize = 9.sp)
                                Text("$${"%.2f".format(sig.takeProfit1)}", color = BullishGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                            Column {
                                Text("Score", color = TextTertiary, fontSize = 9.sp)
                                Text("${sig.setupQualityScore}/100", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (sig.journalNotes.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("Note: ${sig.journalNotes}", color = TextSecondary, fontSize = 11.sp)
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Regime: ${sig.marketRegime}", color = TextTertiary, fontSize = 10.sp)
                            sig.createdAt?.let {
                                Text(dateFormat.format(it.toDate()), color = TextTertiary, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Outcome Edit Dialog
    editingSignal?.let { sig ->
        AlertDialog(
            onDismissRequest = { editingSignal = null },
            title = { Text("Update Signal Outcome", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select verified trade outcome for ${sig.symbol}:", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))

                    val outcomes = listOf("PENDING", "TP1_HIT", "TP2_HIT", "TP3_HIT", "SL_HIT", "INVALIDATED")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        outcomes.take(3).forEach { o ->
                            OutcomeChip(o, selectedOutcome == o) { selectedOutcome = o }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        outcomes.drop(3).forEach { o ->
                            OutcomeChip(o, selectedOutcome == o) { selectedOutcome = o }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Post-trade review notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSignalOutcome(sig.id, selectedOutcome, editNotes)
                        editingSignal = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Update Outcome", color = TerminalBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSignal = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = TerminalSurface
        )
    }
}

@Composable
fun OutcomeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (isSelected) GoldPrimary else TerminalSurfaceVariant, RoundedCornerShape(6.dp))
            .border(1.dp, if (isSelected) GoldPrimary else TerminalBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) TerminalBackground else TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
