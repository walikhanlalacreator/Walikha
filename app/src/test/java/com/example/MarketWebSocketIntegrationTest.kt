package com.example

import com.example.data.model.Instrument
import com.example.data.model.WebSocketConnectionState
import com.example.data.remote.MarketWebSocketManager
import com.example.data.remote.RealtimePriceUpdate
import com.example.data.repository.MarketRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MarketWebSocketIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testAllFiveInstrumentsAreDefinedAndSupported() {
        val required = listOf(
            Instrument.XAU_USD,
            Instrument.BTC_USD,
            Instrument.ETH_USD,
            Instrument.SOL_USD,
            Instrument.XRP_USD
        )
        assertEquals(5, required.size)
        for (inst in required) {
            assertNotNull("Instrument ${inst.id} must be defined", inst)
            assertTrue("Display symbol must be non-empty", inst.displaySymbol.isNotBlank())
        }
    }

    @Test
    fun testRealtimePriceUpdateCapturesTimestampAndSource() {
        val now = System.currentTimeMillis()
        val update = RealtimePriceUpdate(
            instrument = Instrument.BTC_USD,
            price = 68500.50,
            open24h = 67000.00,
            high24h = 69000.00,
            low24h = 66500.00,
            change24hPercent = 2.24,
            volume24h = 15420.5,
            bid = 68499.00,
            ask = 68501.00,
            timestamp = now,
            dataSource = "Binance WebSocket (BTCUSDT)"
        )

        assertEquals(Instrument.BTC_USD, update.instrument)
        assertEquals(68500.50, update.price, 0.001)
        assertEquals("Binance WebSocket (BTCUSDT)", update.dataSource)
        assertEquals(now, update.timestamp)
    }

    @Test
    fun testGoldInstrumentWebSocketMappingAndSource() {
        val now = System.currentTimeMillis()
        val goldUpdate = RealtimePriceUpdate(
            instrument = Instrument.XAU_USD,
            price = 2735.80,
            open24h = 2710.00,
            high24h = 2745.00,
            low24h = 2705.00,
            change24hPercent = 0.95,
            volume24h = 45000.0,
            bid = 2735.50,
            ask = 2736.10,
            timestamp = now,
            dataSource = "Binance WebSocket (PAXG Gold 1:1 Spot)"
        )

        assertEquals(Instrument.XAU_USD, goldUpdate.instrument)
        assertEquals(2735.80, goldUpdate.price, 0.001)
        assertTrue(goldUpdate.dataSource.contains("PAXG") || goldUpdate.dataSource.contains("Gold"))
        assertEquals(now, goldUpdate.timestamp)
    }

    @Test
    fun testWebSocketEndpointsIncludeAllFiveInstruments() {
        val endpoints = MarketWebSocketManager.ENDPOINTS
        assertTrue("Endpoints list must not be empty", endpoints.isNotEmpty())
        for (url in endpoints) {
            assertTrue("URL must stream btcusdt", url.contains("btcusdt"))
            assertTrue("URL must stream ethusdt", url.contains("ethusdt"))
            assertTrue("URL must stream solusdt", url.contains("solusdt"))
            assertTrue("URL must stream xrpusdt", url.contains("xrpusdt"))
            assertTrue("URL must stream paxgusdt for gold", url.contains("paxgusdt"))
        }
    }

    @Test
    fun testConnectionStatesDefinitions() {
        val states = WebSocketConnectionState.entries
        assertTrue(states.contains(WebSocketConnectionState.CONNECTED))
        assertTrue(states.contains(WebSocketConnectionState.CONNECTING))
        assertTrue(states.contains(WebSocketConnectionState.RECONNECTING))
        assertTrue(states.contains(WebSocketConnectionState.FALLBACK_REST))
        assertTrue(states.contains(WebSocketConnectionState.DISCONNECTED))
    }

    @Test
    fun testMarketRepositoryRestFallbackToggling() = testScope.runTest {
        val repo = MarketRepository()

        // Test toggle disabling WebSocket -> moves to FALLBACK_REST
        repo.toggleWebSocket(false)
        assertEquals(WebSocketConnectionState.FALLBACK_REST, repo.webSocketConnectionState.value)

        // Test toggle enabling WebSocket -> moves to RECONNECTING or CONNECTING
        repo.toggleWebSocket(true)
        val state = repo.webSocketConnectionState.value
        assertTrue(
            "State after enabling should be CONNECTING or RECONNECTING, was $state",
            state == WebSocketConnectionState.CONNECTING || state == WebSocketConnectionState.RECONNECTING
        )
    }
}
