package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
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

@Composable
fun AiIntelScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bundle by viewModel.currentBundle.collectAsState()
    val flashText by viewModel.flashScanText.collectAsState()
    val isScanningFlash by viewModel.isScanningFlash.collectAsState()
    val macroText by viewModel.macroNewsText.collectAsState()
    val isSearchingMacro by viewModel.isSearchingMacro.collectAsState()
    val visionText by viewModel.chartVisionText.collectAsState()
    val isAnalyzingImage by viewModel.isAnalyzingImage.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()

    // Photo picker launcher (zero-permission Android Photo Picker compliant with Play Policy)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        viewModel.analyzeChartScreenshot(bitmap, "User uploaded chart screenshot")
                    }
                }
            } catch (_: Exception) {}
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(BlueAccent, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI QUANT INTELLIGENCE",
                        color = GoldPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    "Multi-Modal Reasoning & Grounded Macro Synthesis",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = {
                    if (isTtsSpeaking) viewModel.stopTts() else viewModel.speakSignalSummary(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = TerminalSurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isTtsSpeaking) "Stop TTS" else "Voice TTS", color = TextPrimary, fontSize = 11.sp)
            }
        }

        if (bundle == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldPrimary)
            }
            return
        }

        val b = bundle!!
        val ai = b.geminiAnalysis

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode 1: Deep Thinking Mode (gemini-3.1-pro-preview with thinkingLevel = HIGH)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("deep_thinking_card"),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("High Thinking Analysis", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("gemini-3.1-pro-preview (thinkingLevel = high)", color = GoldPrimary, fontSize = 10.sp)
                                }
                            }

                            Button(
                                onClick = { viewModel.runAiDeepThinking() },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !b.isAnalyzingWithAi
                            ) {
                                if (b.isAnalyzingWithAi) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TerminalBackground, strokeWidth = 2.dp)
                                } else {
                                    Text("Run Deep Think", color = TerminalBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        if (ai != null) {
                            Text(
                                text = "Market Regime: ${ai.marketRegime} • HTF Bias: ${ai.higherTimeframeBias}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = ai.profileAnalysis,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = ai.structureAnalysis,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )

                            if (ai.bullishEvidence.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Bullish Drivers:", color = BullishGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                ai.bullishEvidence.forEach {
                                    Text("• $it", color = TextSecondary, fontSize = 11.sp)
                                }
                            }

                            if (ai.bearishEvidence.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text("Bearish Drivers:", color = BearishRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                ai.bearishEvidence.forEach {
                                    Text("• $it", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        } else {
                            Text(
                                "Tap 'Run Deep Think' to invoke Gemini 3.1 Pro with high thinking mode for mathematical profile synthesis, order book liquidity sweeps, and scenario mapping.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Mode 2: Low-Latency Responses (gemini-3.1-flash-lite)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("low_latency_card"),
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
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Low-Latency Flash Scan", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("gemini-3.1-flash-lite-preview", color = BlueAccent, fontSize = 10.sp)
                                }
                            }

                            Button(
                                onClick = { viewModel.runFlashScan() },
                                colors = ButtonDefaults.buttonColors(containerColor = TerminalSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isScanningFlash
                            ) {
                                if (isScanningFlash) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = BlueAccent, strokeWidth = 2.dp)
                                } else {
                                    Text("Quick Scan", color = TextPrimary, fontSize = 11.sp)
                                }
                            }
                        }

                        flashText?.let { t ->
                            Spacer(Modifier.height(8.dp))
                            Text(t, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            // Mode 3: Google Search Grounding (gemini-3.5-flash with googleSearch)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("search_grounding_card"),
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
                                Icon(Icons.Default.Language, contentDescription = null, tint = BullishGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Search Grounded Macro", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("gemini-3.5-flash + Google Search", color = BullishGreen, fontSize = 10.sp)
                                }
                            }

                            Button(
                                onClick = { viewModel.runMacroSearch() },
                                colors = ButtonDefaults.buttonColors(containerColor = TerminalSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isSearchingMacro
                            ) {
                                if (isSearchingMacro) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = BullishGreen, strokeWidth = 2.dp)
                                } else {
                                    Text("Fetch News", color = TextPrimary, fontSize = 11.sp)
                                }
                            }
                        }

                        macroText?.let { t ->
                            Spacer(Modifier.height(8.dp))
                            Text(t, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            // Mode 4: Image Chart Vision Analysis (gemini-3.1-pro-preview)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("image_analysis_card"),
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
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Chart Screenshot Vision", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("gemini-3.1-pro-preview image understanding", color = GoldPrimary, fontSize = 10.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TerminalSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                enabled = !isAnalyzingImage
                            ) {
                                if (isAnalyzingImage) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = GoldPrimary, strokeWidth = 2.dp)
                                } else {
                                    Text("Upload Chart", color = TextPrimary, fontSize = 11.sp)
                                }
                            }
                        }

                        visionText?.let { t ->
                            Spacer(Modifier.height(8.dp))
                            Text(t, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
