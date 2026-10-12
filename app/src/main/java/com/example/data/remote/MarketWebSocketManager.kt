package com.example.data.remote

import android.util.Log
import com.example.data.model.Instrument
import com.example.data.model.WebSocketConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class RealtimePriceUpdate(
    val instrument: Instrument,
    val price: Double,
    val open24h: Double,
    val high24h: Double,
    val low24h: Double,
    val change24hPercent: Double,
    val volume24h: Double,
    val bid: Double,
    val ask: Double,
    val timestamp: Long,
    val dataSource: String = "WebSocket Live Stream"
)

class MarketWebSocketManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    companion object {
        private const val TAG = "MarketWebSocket"
        private const val WATCHDOG_CHECK_INTERVAL_MS = 10_000L
        private const val INACTIVITY_TIMEOUT_MS = 25_000L
        private const val MIN_RECONNECT_DELAY_MS = 2_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L

        // Primary and backup stream URLs
        val ENDPOINTS = listOf(
            "wss://stream.binance.com:9443/stream?streams=btcusdt@ticker/ethusdt@ticker/solusdt@ticker/xrpusdt@ticker/paxgusdt@ticker",
            "wss://data-stream.binance.vision/stream?streams=btcusdt@ticker/ethusdt@ticker/solusdt@ticker/xrpusdt@ticker/paxgusdt@ticker"
        )
    }

    private var activeScope: CoroutineScope? = null
    private var webSocket: WebSocket? = null
    private var watchdogJob: Job? = null
    private var reconnectJob: Job? = null

    private val isEnabled = AtomicBoolean(true)
    private val endpointIndex = AtomicInteger(0)
    private val retryAttempts = AtomicInteger(0)

    private val _connectionState = MutableStateFlow(WebSocketConnectionState.CONNECTING)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    private val _priceUpdates = MutableSharedFlow<RealtimePriceUpdate>(replay = 1, extraBufferCapacity = 64)
    val priceUpdates: SharedFlow<RealtimePriceUpdate> = _priceUpdates.asSharedFlow()

    private val _lastSuccessfulUpdate = MutableStateFlow<Map<Instrument, Long>>(emptyMap())
    val lastSuccessfulUpdate: StateFlow<Map<Instrument, Long>> = _lastSuccessfulUpdate.asStateFlow()

    private val _globalLastTimestamp = MutableStateFlow(0L)
    val globalLastTimestamp: StateFlow<Long> = _globalLastTimestamp.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    // Listener for fallback notifications
    private var onFallbackRequested: (() -> Unit)? = null

    fun setFallbackListener(listener: () -> Unit) {
        onFallbackRequested = listener
    }

    @Synchronized
    fun start(scope: CoroutineScope) {
        activeScope = scope
        isEnabled.set(true)
        retryAttempts.set(0)
        connectInternal()
        startWatchdog()
    }

    @Synchronized
    fun stop() {
        isEnabled.set(false)
        watchdogJob?.cancel()
        watchdogJob = null
        reconnectJob?.cancel()
        reconnectJob = null

        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _connectionState.value = WebSocketConnectionState.DISCONNECTED
    }

    @Synchronized
    fun reconnect() {
        retryAttempts.set(0)
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.cancel()
        webSocket = null
        _connectionState.value = WebSocketConnectionState.RECONNECTING
        connectInternal()
    }

    fun setEnabled(enabled: Boolean) {
        if (isEnabled.getAndSet(enabled) != enabled) {
            if (enabled) {
                reconnect()
            } else {
                stop()
                _connectionState.value = WebSocketConnectionState.FALLBACK_REST
                onFallbackRequested?.invoke()
            }
        }
    }

    private fun connectInternal() {
        if (!isEnabled.get()) return

        val endpoints = ENDPOINTS
        val currentIdx = endpointIndex.get() % endpoints.size
        val url = endpoints[currentIdx]

        _connectionState.value = WebSocketConnectionState.CONNECTING
        _lastErrorMessage.value = null

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected successfully to $url")
                retryAttempts.set(0)
                _connectionState.value = WebSocketConnectionState.CONNECTED
                _lastErrorMessage.value = null
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closed: $code / $reason")
                handleDisconnect("Connection closed: $reason ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = t.message ?: "Connection failure"
                Log.e(TAG, "WebSocket failure: $msg", t)
                _lastErrorMessage.value = msg
                handleDisconnect("WebSocket error: $msg")
            }
        })
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val root = JSONObject(text)
            val dataObj = if (root.has("data")) root.getJSONObject("data") else root

            val symbol = dataObj.optString("s", "").uppercase()
            val instrument = mapSymbolToInstrument(symbol) ?: return

            val now = System.currentTimeMillis()
            val eventTime = dataObj.optLong("E", now)

            val price = parseDouble(dataObj, "c")
            if (price <= 0.0) return

            val open = parseDouble(dataObj, "o", price)
            val high = parseDouble(dataObj, "h", price)
            val low = parseDouble(dataObj, "l", price)
            val changePct = parseDouble(dataObj, "P", 0.0)
            val volume = parseDouble(dataObj, "v", 0.0)
            val bid = parseDouble(dataObj, "b", price)
            val ask = parseDouble(dataObj, "a", price)

            val providerName = when (instrument) {
                Instrument.XAU_USD -> "Binance WebSocket (PAXG Gold 1:1 Spot)"
                else -> "Binance WebSocket (${symbol})"
            }

            val update = RealtimePriceUpdate(
                instrument = instrument,
                price = price,
                open24h = open,
                high24h = high,
                low24h = low,
                change24hPercent = changePct,
                volume24h = volume,
                bid = bid,
                ask = ask,
                timestamp = if (eventTime > 0) eventTime else now,
                dataSource = providerName
            )

            // Successfully received a real tick
            _connectionState.value = WebSocketConnectionState.CONNECTED
            _globalLastTimestamp.value = now

            val currentMap = _lastSuccessfulUpdate.value.toMutableMap()
            currentMap[instrument] = now
            _lastSuccessfulUpdate.value = currentMap

            activeScope?.launch {
                _priceUpdates.emit(update)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing WebSocket payload: ${e.message}")
        }
    }

    private fun parseDouble(json: JSONObject, key: String, fallback: Double = 0.0): Double {
        if (!json.has(key)) return fallback
        return try {
            val str = json.optString(key, "")
            if (str.isNotBlank()) str.toDouble() else json.optDouble(key, fallback)
        } catch (_: Exception) {
            json.optDouble(key, fallback)
        }
    }

    private fun mapSymbolToInstrument(symbol: String): Instrument? {
        return when (symbol) {
            "BTCUSDT" -> Instrument.BTC_USD
            "ETHUSDT" -> Instrument.ETH_USD
            "SOLUSDT" -> Instrument.SOL_USD
            "XRPUSDT" -> Instrument.XRP_USD
            "PAXGUSDT", "XAUUSDT", "XAUUSD" -> Instrument.XAU_USD
            else -> null
        }
    }

    private fun handleDisconnect(reason: String) {
        if (!isEnabled.get()) return

        val attempts = retryAttempts.incrementAndGet()
        // If consecutive errors, switch endpoint to backup
        if (attempts % 2 == 0) {
            endpointIndex.incrementAndGet()
        }

        // Immediately transition state and trigger REST fallback
        _connectionState.value = if (attempts >= 2) {
            WebSocketConnectionState.FALLBACK_REST
        } else {
            WebSocketConnectionState.RECONNECTING
        }

        Log.w(TAG, "WebSocket disconnected ($reason). Attempt $attempts. Triggering REST fallback.")
        onFallbackRequested?.invoke()

        // Schedule recovery with exponential backoff
        scheduleReconnect(attempts)
    }

    private fun scheduleReconnect(attempt: Int) {
        val scope = activeScope ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            val delayMs = calculateBackoff(attempt)
            Log.i(TAG, "Will attempt reconnect in ${delayMs}ms (attempt $attempt)")
            delay(delayMs)
            if (isActive && isEnabled.get()) {
                connectInternal()
            }
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        val factor = 1L shl (attempt - 1).coerceIn(0, 5)
        val computed = MIN_RECONNECT_DELAY_MS * factor
        return computed.coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    private fun startWatchdog() {
        val scope = activeScope ?: return
        watchdogJob?.cancel()
        watchdogJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(WATCHDOG_CHECK_INTERVAL_MS)
                if (!isEnabled.get()) continue

                val now = System.currentTimeMillis()
                val lastMsg = _globalLastTimestamp.value

                // If in CONNECTED state but no messages received for > INACTIVITY_TIMEOUT_MS, reconnect
                if (_connectionState.value == WebSocketConnectionState.CONNECTED &&
                    lastMsg > 0 &&
                    (now - lastMsg) > INACTIVITY_TIMEOUT_MS
                ) {
                    Log.w(TAG, "Watchdog detected inactive stream for ${now - lastMsg}ms. Reconnecting...")
                    _connectionState.value = WebSocketConnectionState.FALLBACK_REST
                    onFallbackRequested?.invoke()
                    reconnect()
                }
            }
        }
    }
}
