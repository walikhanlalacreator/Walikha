package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.engine.BacktestEngine
import com.example.data.engine.MarketProfileEngine
import com.example.data.engine.RiskCalculatorEngine
import com.example.data.engine.SignalEngine
import com.example.data.engine.VolumeProfileEngine
import com.example.data.model.BacktestSummary
import com.example.data.model.Candle
import com.example.data.model.DataStatus
import com.example.data.model.Instrument
import com.example.data.model.MarketProfileResult
import com.example.data.model.MultiTimeframeAnalysis
import com.example.data.model.RiskCalculationResult
import com.example.data.model.RiskParameters
import com.example.data.model.Signal
import com.example.data.model.Timeframe
import com.example.data.model.VolumeProfileResult
import com.example.data.model.WebSocketConnectionState
import com.example.data.remote.GeminiApiService
import com.example.data.remote.GeminiStructuredAnalysis
import com.example.data.remote.MarketDataService
import com.example.data.remote.MarketSnapshot
import com.example.data.remote.MarketWebSocketManager
import com.example.data.remote.RealtimePriceUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class InstrumentAnalysisBundle(
    val instrument: Instrument,
    val snapshot: MarketSnapshot,
    val marketProfile: MarketProfileResult,
    val volumeProfile: VolumeProfileResult,
    val signal: Signal,
    val mtfAnalysis: MultiTimeframeAnalysis,
    val geminiAnalysis: GeminiStructuredAnalysis? = null,
    val backtestSummary: BacktestSummary? = null,
    val isAnalyzingWithAi: Boolean = false,
    val isRunningBacktest: Boolean = false
)

class MarketRepository(
    private val dataService: MarketDataService = MarketDataService(),
    private val geminiService: GeminiApiService = GeminiApiService(),
    val webSocketManager: MarketWebSocketManager = MarketWebSocketManager()
) {
    private val _bundles = ConcurrentHashMap<Instrument, InstrumentAnalysisBundle>()

    private val _currentInstrument = MutableStateFlow(Instrument.XAU_USD)
    val currentInstrument: StateFlow<Instrument> = _currentInstrument.asStateFlow()

    private val _allInstrumentsSnapshots = MutableStateFlow<Map<Instrument, MarketSnapshot>>(emptyMap())
    val allInstrumentsSnapshots: StateFlow<Map<Instrument, MarketSnapshot>> = _allInstrumentsSnapshots.asStateFlow()

    private val _currentBundle = MutableStateFlow<InstrumentAnalysisBundle?>(null)
    val currentBundle: StateFlow<InstrumentAnalysisBundle?> = _currentBundle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // WebSocket state forwarding
    val webSocketConnectionState: StateFlow<WebSocketConnectionState> = webSocketManager.connectionState
    val globalLastUpdateTimestamp: StateFlow<Long> = webSocketManager.globalLastTimestamp
    val lastUpdateByInstrument: StateFlow<Map<Instrument, Long>> = webSocketManager.lastSuccessfulUpdate

    private var activeScope: CoroutineScope? = null
    private var restFallbackPollingJob: Job? = null
    private var isWebSocketStarted = false

    fun selectInstrument(instrument: Instrument) {
        _currentInstrument.value = instrument
        _currentBundle.value = _bundles[instrument]
    }

    /**
     * Starts WebSocket connection and begins listening for real-time price ticks for
     * XAU/USD, BTC/USD, ETH/USD, SOL/USD, and XRP/USD.
     * Sets up automatic fallback to REST API when WebSockets are unavailable.
     */
    fun startWebSocketStream(scope: CoroutineScope) {
        if (isWebSocketStarted) return
        isWebSocketStarted = true
        activeScope = scope

        // Hook up REST fallback trigger from WebSocket manager
        webSocketManager.setFallbackListener {
            scope.launch(Dispatchers.IO) {
                triggerRestFallback()
            }
        }

        // Start WebSocket connection
        webSocketManager.start(scope)

        // Collect incoming real-time price ticks
        scope.launch(Dispatchers.IO) {
            webSocketManager.priceUpdates.collect { update ->
                handleRealtimePriceUpdate(update)
            }
        }

        // Start periodic fallback polling watchdog
        startRestFallbackPoller(scope)
    }

    fun reconnectWebSocket() {
        webSocketManager.reconnect()
    }

    fun toggleWebSocket(enabled: Boolean) {
        webSocketManager.setEnabled(enabled)
    }

    private fun handleRealtimePriceUpdate(update: RealtimePriceUpdate) {
        val inst = update.instrument
        val existingSnapshot = _allInstrumentsSnapshots.value[inst]

        val updatedSnapshot = if (existingSnapshot != null) {
            // Update candle closes with latest tick
            val updatedCandlesMap = existingSnapshot.candlesByTimeframe.mapValues { (_, candles) ->
                if (candles.isEmpty()) emptyList()
                else {
                    val last = candles.last()
                    val updatedLast = last.copy(
                        close = update.price,
                        high = maxOf(last.high, update.price),
                        low = if (last.low > 0) minOf(last.low, update.price) else update.price
                    )
                    candles.dropLast(1) + updatedLast
                }
            }

            existingSnapshot.copy(
                currentPrice = update.price,
                open24h = if (update.open24h > 0) update.open24h else existingSnapshot.open24h,
                high24h = maxOf(existingSnapshot.high24h, update.high24h, update.price),
                low24h = if (update.low24h > 0) minOf(existingSnapshot.low24h, update.low24h) else existingSnapshot.low24h,
                change24hPercent = update.change24hPercent,
                volume24h = if (update.volume24h > 0) update.volume24h else existingSnapshot.volume24h,
                bid = update.bid,
                ask = update.ask,
                spread = (update.ask - update.bid).coerceAtLeast(0.0),
                candlesByTimeframe = updatedCandlesMap,
                dataProvider = update.dataSource,
                lastUpdateTimestamp = update.timestamp,
                status = DataStatus.LIVE,
                statusMessage = null,
                isWebSocket = true,
                connectionState = WebSocketConnectionState.CONNECTED
            )
        } else {
            // Initial placeholder snapshot until full candles arrive via REST
            MarketSnapshot(
                instrument = inst,
                currentPrice = update.price,
                open24h = update.open24h,
                high24h = update.high24h,
                low24h = update.low24h,
                change24hPercent = update.change24hPercent,
                volume24h = update.volume24h,
                bid = update.bid,
                ask = update.ask,
                spread = (update.ask - update.bid).coerceAtLeast(0.0),
                candlesByTimeframe = emptyMap(),
                dataProvider = update.dataSource,
                lastUpdateTimestamp = update.timestamp,
                status = DataStatus.LIVE,
                statusMessage = null,
                isWebSocket = true,
                connectionState = WebSocketConnectionState.CONNECTED
            )
        }

        // Update snapshots map
        val snapshots = _allInstrumentsSnapshots.value.toMutableMap()
        snapshots[inst] = updatedSnapshot
        _allInstrumentsSnapshots.value = snapshots

        // Update bundle if we have candles
        if (updatedSnapshot.candlesByTimeframe.isNotEmpty()) {
            processSnapshot(inst, updatedSnapshot)
        }

        if (inst == _currentInstrument.value) {
            _currentBundle.value = _bundles[inst]
        }
    }

    private fun startRestFallbackPoller(scope: CoroutineScope) {
        restFallbackPollingJob?.cancel()
        restFallbackPollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val state = webSocketManager.connectionState.value
                val isFallback = (state == WebSocketConnectionState.FALLBACK_REST ||
                        state == WebSocketConnectionState.RECONNECTING ||
                        state == WebSocketConnectionState.DISCONNECTED ||
                        _allInstrumentsSnapshots.value.isEmpty())

                val intervalMs = if (isFallback) 12_000L else 60_000L
                delay(intervalMs)

                if (isActive) {
                    // Poll via REST API
                    refreshAllInstrumentsViaRest(isFallback = isFallback)
                }
            }
        }
    }

    private suspend fun triggerRestFallback() {
        refreshAllInstrumentsViaRest(isFallback = true)
    }

    private suspend fun refreshAllInstrumentsViaRest(isFallback: Boolean) {
        try {
            val snapshots = _allInstrumentsSnapshots.value.toMutableMap()
            for (inst in Instrument.entries) {
                val fresh = dataService.fetchMarketSnapshot(inst, Timeframe.M15)
                val isWsActive = (webSocketManager.connectionState.value == WebSocketConnectionState.CONNECTED)

                // If WebSocket is actively connected and has newer data, preserve WebSocket provider tag
                val providerLabel = if (isFallback || !isWsActive) {
                    "${fresh.dataProvider} (REST Fallback)"
                } else {
                    fresh.dataProvider
                }

                val tagged = fresh.copy(
                    dataProvider = providerLabel,
                    isWebSocket = (!isFallback && isWsActive),
                    connectionState = webSocketManager.connectionState.value
                )

                snapshots[inst] = tagged
                processSnapshot(inst, tagged)
            }
            _allInstrumentsSnapshots.value = snapshots
            _currentBundle.value = _bundles[_currentInstrument.value]
        } catch (e: Exception) {
            _errorMessage.value = "REST Fallback sync error: ${e.message}"
        }
    }

    suspend fun refreshAllInstruments() {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val isWsActive = (webSocketManager.connectionState.value == WebSocketConnectionState.CONNECTED)
            val snapshots = mutableMapOf<Instrument, MarketSnapshot>()
            for (inst in Instrument.entries) {
                val snapshot = dataService.fetchMarketSnapshot(inst, Timeframe.M15)
                val providerLabel = if (isWsActive) {
                    "${snapshot.dataProvider} [WS Active]"
                } else {
                    "${snapshot.dataProvider} (REST Feed)"
                }
                val tagged = snapshot.copy(
                    dataProvider = providerLabel,
                    isWebSocket = isWsActive,
                    connectionState = webSocketManager.connectionState.value
                )
                snapshots[inst] = tagged
                processSnapshot(inst, tagged)
            }
            _allInstrumentsSnapshots.value = snapshots
            _currentBundle.value = _bundles[_currentInstrument.value]
        } catch (e: Exception) {
            _errorMessage.value = "Failed to refresh market data: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun refreshCurrentInstrument(selectedTimeframe: Timeframe = Timeframe.M15) {
        val inst = _currentInstrument.value
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val isWsActive = (webSocketManager.connectionState.value == WebSocketConnectionState.CONNECTED)
            val snapshot = dataService.fetchMarketSnapshot(inst, selectedTimeframe)
            val tagged = snapshot.copy(
                isWebSocket = isWsActive,
                connectionState = webSocketManager.connectionState.value
            )
            processSnapshot(inst, tagged)
            _currentBundle.value = _bundles[inst]

            val currentMap = _allInstrumentsSnapshots.value.toMutableMap()
            currentMap[inst] = tagged
            _allInstrumentsSnapshots.value = currentMap
        } catch (e: Exception) {
            _errorMessage.value = "Error refreshing ${inst.displaySymbol}: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    private fun processSnapshot(instrument: Instrument, snapshot: MarketSnapshot) {
        val candles15m = snapshot.candlesByTimeframe[Timeframe.M15] ?: emptyList()
        val candles5m = snapshot.candlesByTimeframe[Timeframe.M5] ?: emptyList()
        val candles1h = snapshot.candlesByTimeframe[Timeframe.H1] ?: emptyList()
        val candles4h = snapshot.candlesByTimeframe[Timeframe.H4] ?: emptyList()

        val primaryCandles = if (candles15m.isNotEmpty()) candles15m else candles5m

        val marketProfile = MarketProfileEngine.calculate(
            candles = primaryCandles,
            tickSize = instrument.defaultTickSize,
            valueAreaPercentage = 0.70,
            sessionDescription = if (instrument.isCrypto) "24/7 Continuous UTC Crypto Session" else "New York & London Spot Trading Session"
        )

        val volumeProfile = VolumeProfileEngine.calculate(
            candles = primaryCandles,
            instrument = instrument,
            tickSize = instrument.defaultTickSize,
            valueAreaPercentage = 0.70
        )

        val (signal, mtf) = SignalEngine.generateSignal(
            instrument = instrument,
            h4Candles = candles4h,
            h1Candles = candles1h,
            m15Candles = candles15m,
            m5Candles = candles5m,
            marketProfile = marketProfile,
            volumeProfile = volumeProfile,
            dataStatus = snapshot.status
        )

        val existingBundle = _bundles[instrument]
        val bundle = InstrumentAnalysisBundle(
            instrument = instrument,
            snapshot = snapshot,
            marketProfile = marketProfile,
            volumeProfile = volumeProfile,
            signal = signal,
            mtfAnalysis = mtf,
            geminiAnalysis = existingBundle?.geminiAnalysis,
            backtestSummary = existingBundle?.backtestSummary
        )

        _bundles[instrument] = bundle
    }

    suspend fun runAiDeepThinkingAnalysis() {
        val bundle = _currentBundle.value ?: return
        _currentBundle.value = bundle.copy(isAnalyzingWithAi = true)
        try {
            val aiResult = geminiService.analyzeWithDeepThinking(
                signal = bundle.signal,
                profile = bundle.marketProfile,
                volumeProfile = bundle.volumeProfile,
                mtf = bundle.mtfAnalysis
            )
            val updated = bundle.copy(
                geminiAnalysis = aiResult,
                isAnalyzingWithAi = false
            )
            _bundles[bundle.instrument] = updated
            _currentBundle.value = updated
        } catch (e: Exception) {
            _currentBundle.value = bundle.copy(isAnalyzingWithAi = false)
            _errorMessage.value = "AI Analysis error: ${e.message}"
        }
    }

    suspend fun runFlashLiteScan(instrument: Instrument, price: Double, signal: String): String {
        return geminiService.quickScanLowLatency(instrument, price, signal)
    }

    suspend fun fetchSearchGroundedMacroContext(instrument: Instrument): String {
        return geminiService.getSearchGroundedMacroContext(instrument)
    }

    suspend fun analyzeChartScreenshot(bitmap: Bitmap, notes: String = ""): String {
        return geminiService.analyzeChartImage(bitmap, _currentInstrument.value, notes)
    }

    fun runBacktestOnCurrent(): BacktestSummary {
        val bundle = _currentBundle.value ?: return BacktestEngine.runBacktest(Instrument.XAU_USD, emptyList())
        val candles = bundle.snapshot.candlesByTimeframe[Timeframe.M15]
            ?: bundle.snapshot.candlesByTimeframe[Timeframe.M5]
            ?: emptyList()

        val summary = BacktestEngine.runBacktest(
            instrument = bundle.instrument,
            historicalCandles = candles
        )

        val updated = bundle.copy(backtestSummary = summary)
        _bundles[bundle.instrument] = updated
        _currentBundle.value = updated
        return summary
    }

    fun calculateRisk(params: RiskParameters): RiskCalculationResult {
        return RiskCalculatorEngine.calculate(params)
    }
}
