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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DataStatus
import com.example.data.model.Instrument
import com.example.data.model.WebSocketConnectionState
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
fun DashboardScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val snapshots by viewModel.allSnapshots.collectAsState()
    val isLoading by viewModel.isMarketLoading.collectAsState()
    val error by viewModel.marketError.collectAsState()
    val selectedInstrument by viewModel.selectedInstrument.collectAsState()
    val wsState by viewModel.webSocketConnectionState.collectAsState()
    val globalLastTimestamp by viewModel.globalLastUpdateTimestamp.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(horizontal = 14.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(GoldPrimary, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "GOLD PROFILE AI",
                        color = GoldPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Market Profile & Volume Order Flow Terminal",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = { viewModel.refreshMarkets() },
                modifier = Modifier
                    .testTag("refresh_markets_button")
                    .background(TerminalSurfaceVariant, CircleShape)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GoldPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = GoldPrimary)
                }
            }
        }

        // Dedicated WebSocket Connection Status & Data Source Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("websocket_status_banner"),
            colors = CardDefaults.cardColors(
                containerColor = when (wsState) {
                    WebSocketConnectionState.CONNECTED -> BullishGreen.copy(0.08f)
                    WebSocketConnectionState.FALLBACK_REST -> BlueAccent.copy(0.08f)
                    WebSocketConnectionState.RECONNECTING,
                    WebSocketConnectionState.CONNECTING -> GoldPrimary.copy(0.08f)
                    WebSocketConnectionState.DISCONNECTED -> BearishRed.copy(0.08f)
                }
            ),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    when (wsState) {
                        WebSocketConnectionState.CONNECTED -> BullishGreen.copy(0.4f)
                        WebSocketConnectionState.FALLBACK_REST -> BlueAccent.copy(0.4f)
                        WebSocketConnectionState.RECONNECTING,
                        WebSocketConnectionState.CONNECTING -> GoldPrimary.copy(0.4f)
                        WebSocketConnectionState.DISCONNECTED -> BearishRed.copy(0.4f)
                    }
                )
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    when (wsState) {
                                        WebSocketConnectionState.CONNECTED -> BullishGreen
                                        WebSocketConnectionState.FALLBACK_REST -> BlueAccent
                                        WebSocketConnectionState.RECONNECTING,
                                        WebSocketConnectionState.CONNECTING -> GoldPrimary
                                        WebSocketConnectionState.DISCONNECTED -> BearishRed
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (wsState) {
                                WebSocketConnectionState.CONNECTED -> "REAL-TIME WEBSOCKET STREAM"
                                WebSocketConnectionState.FALLBACK_REST -> "REST API FALLBACK ACTIVE"
                                WebSocketConnectionState.RECONNECTING -> "WEBSOCKET RECONNECTING..."
                                WebSocketConnectionState.CONNECTING -> "CONNECTING WEBSOCKET..."
                                WebSocketConnectionState.DISCONNECTED -> "STREAM DISCONNECTED"
                            },
                            color = when (wsState) {
                                WebSocketConnectionState.CONNECTED -> BullishGreen
                                WebSocketConnectionState.FALLBACK_REST -> BlueAccent
                                WebSocketConnectionState.RECONNECTING,
                                WebSocketConnectionState.CONNECTING -> GoldPrimary
                                WebSocketConnectionState.DISCONNECTED -> BearishRed
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Reconnect / Toggle Button
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (wsState != WebSocketConnectionState.CONNECTED) {
                            Button(
                                onClick = { viewModel.reconnectWebSocket() },
                                modifier = Modifier
                                    .testTag("reconnect_ws_button")
                                    .height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldPrimary,
                                    contentColor = TerminalBackground
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reconnect", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val shouldEnable = (wsState == WebSocketConnectionState.FALLBACK_REST || wsState == WebSocketConnectionState.DISCONNECTED)
                                viewModel.toggleWebSocket(shouldEnable)
                            },
                            modifier = Modifier
                                .testTag("toggle_feed_button")
                                .height(28.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (wsState == WebSocketConnectionState.CONNECTED) "Force REST" else "Use WS",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (wsState) {
                            WebSocketConnectionState.CONNECTED -> "Active Source: Binance WebSocket (XAU, BTC, ETH, SOL, XRP)"
                            WebSocketConnectionState.FALLBACK_REST -> "Active Source: Binance & Yahoo REST APIs (12s auto-sync)"
                            WebSocketConnectionState.RECONNECTING -> "Active Source: Re-establishing socket (REST syncing)"
                            WebSocketConnectionState.CONNECTING -> "Active Source: Negotiating WSS handshake"
                            WebSocketConnectionState.DISCONNECTED -> "Active Source: Disconnected (REST available)"
                        },
                        color = TextSecondary,
                        fontSize = 10.sp
                    )

                    val tsDisplay = if (globalLastTimestamp > 0) {
                        timeFormat.format(Date(globalLastTimestamp))
                    } else {
                        "Syncing..."
                    }
                    Text(
                        text = "Last Tick: $tsDisplay",
                        color = TextTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("ws_last_update_timestamp")
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        error?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BearishRed.copy(0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, BearishRed, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = BearishRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(err, color = BearishRed, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "WATCHLIST MARKETS",
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            )
            Text(
                "${Instrument.entries.size} Verified Instruments",
                color = TextTertiary,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        // Markets List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(Instrument.entries) { inst ->
                val snapshot = snapshots[inst]
                val isSelected = (inst == selectedInstrument)
                MarketCard(
                    instrument = inst,
                    snapshot = snapshot,
                    isSelected = isSelected,
                    onSelect = {
                        viewModel.selectInstrument(inst)
                        viewModel.setTab(NavTab.TERMINAL)
                    }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun MarketCard(
    instrument: Instrument,
    snapshot: com.example.data.remote.MarketSnapshot?,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val price = snapshot?.currentPrice ?: 0.0
    val changePct = snapshot?.change24hPercent ?: 0.0
    val isPositive = changePct >= 0
    val changeColor = if (isPositive) BullishGreen else BearishRed
    val dataStatus = snapshot?.status ?: DataStatus.UNAVAILABLE
    val isWs = snapshot?.isWebSocket ?: false
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("market_card_${instrument.id}")
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = TerminalSurface),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) GoldPrimary else TerminalBorder)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Symbol + Name + Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = instrument.displaySymbol,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(6.dp))

                        // WebSocket Live vs REST Fallback badge
                        Box(
                            modifier = Modifier
                                .testTag("market_feed_badge_${instrument.id}")
                                .background(
                                    if (isWs) BullishGreen.copy(0.18f) else BlueAccent.copy(0.18f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
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
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        // Status badge (LIVE/STALE)
                        Box(
                            modifier = Modifier
                                .testTag("market_status_badge_${instrument.id}")
                                .background(
                                    when (dataStatus) {
                                        DataStatus.LIVE -> BullishGreen.copy(0.12f)
                                        DataStatus.STALE -> GoldPrimary.copy(0.12f)
                                        else -> BearishRed.copy(0.12f)
                                    },
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = dataStatus.name,
                                color = when (dataStatus) {
                                    DataStatus.LIVE -> BullishGreen
                                    DataStatus.STALE -> GoldPrimary
                                    else -> BearishRed
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "${instrument.assetName} • Quote: ${instrument.quoteCurrency}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (price > 0) "$${"%.2f".format(price)}" else "Fetching...",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("market_price_${instrument.id}")
                    )
                    Text(
                        text = "${if (isPositive) "+" else ""}${"%.2f".format(changePct)}%",
                        color = changeColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Footer metadata: Data Source, Timestamp, and Terminal CTA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TerminalSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Source: ${snapshot?.dataProvider ?: "Connecting stream..."}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.testTag("market_source_${instrument.id}")
                    )
                    val updateTimestamp = snapshot?.lastUpdateTimestamp ?: 0L
                    Text(
                        text = if (updateTimestamp > 0) {
                            "Updated: ${timeFormat.format(Date(updateTimestamp))}"
                        } else {
                            "Updated: Awaiting first tick"
                        },
                        color = TextTertiary,
                        fontSize = 9.sp,
                        modifier = Modifier.testTag("market_timestamp_${instrument.id}")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Analyze Profile",
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
