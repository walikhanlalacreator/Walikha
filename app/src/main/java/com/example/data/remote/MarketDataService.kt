package com.example.data.remote

import com.example.data.model.Candle
import com.example.data.model.DataStatus
import com.example.data.model.Instrument
import com.example.data.model.Timeframe
import com.example.data.model.WebSocketConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MarketSnapshot(
    val instrument: Instrument,
    val currentPrice: Double,
    val open24h: Double,
    val high24h: Double,
    val low24h: Double,
    val change24hPercent: Double,
    val volume24h: Double,
    val bid: Double,
    val ask: Double,
    val spread: Double,
    val candlesByTimeframe: Map<Timeframe, List<Candle>>,
    val dataProvider: String,
    val lastUpdateTimestamp: Long,
    val status: DataStatus,
    val statusMessage: String? = null,
    val isWebSocket: Boolean = false,
    val connectionState: WebSocketConnectionState = WebSocketConnectionState.CONNECTED
)

class MarketDataService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchMarketSnapshot(
        instrument: Instrument,
        selectedTimeframe: Timeframe = Timeframe.M15
    ): MarketSnapshot = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        try {
            if (instrument.isCrypto && instrument.binanceSymbol != null) {
                fetchBinanceSnapshot(instrument, selectedTimeframe)
            } else {
                fetchGoldSnapshot(instrument, selectedTimeframe)
            }
        } catch (e: Exception) {
            MarketSnapshot(
                instrument = instrument,
                currentPrice = 0.0,
                open24h = 0.0,
                high24h = 0.0,
                low24h = 0.0,
                change24hPercent = 0.0,
                volume24h = 0.0,
                bid = 0.0,
                ask = 0.0,
                spread = 0.0,
                candlesByTimeframe = emptyMap(),
                dataProvider = if (instrument.isCrypto) "Binance API" else "Yahoo Gold Quotes",
                lastUpdateTimestamp = now,
                status = DataStatus.UNAVAILABLE,
                statusMessage = "MARKET DATA UNAVAILABLE: ${e.message ?: "Connection failure"}"
            )
        }
    }

    private fun fetchBinanceSnapshot(
        instrument: Instrument,
        primaryTimeframe: Timeframe,
        overrideSymbol: String? = null
    ): MarketSnapshot {
        val symbol = overrideSymbol ?: instrument.binanceSymbol ?: "BTCUSDT"
        val timeframes = listOf(Timeframe.H4, Timeframe.H1, Timeframe.M15, Timeframe.M5)
        val candleMap = mutableMapOf<Timeframe, List<Candle>>()

        // Try primary public endpoint and backup vision endpoint
        val baseUrls = listOf("https://api.binance.com", "https://data-api.binance.vision")
        var successBaseUrl: String? = null

        for (base in baseUrls) {
            try {
                val pingReq = Request.Builder().url("$base/api/v3/ping").build()
                client.newCall(pingReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        successBaseUrl = base
                    }
                }
                if (successBaseUrl != null) break
            } catch (_: Exception) {}
        }

        val baseUrl = successBaseUrl ?: "https://api.binance.com"

        // Fetch 24h ticker for price, high, low, volume, bid, ask
        val tickerReq = Request.Builder().url("$baseUrl/api/v3/ticker/24hr?symbol=$symbol").build()
        var currentPrice = 0.0
        var open24h = 0.0
        var high24h = 0.0
        var low24h = 0.0
        var changePct = 0.0
        var vol24h = 0.0
        var bid = 0.0
        var ask = 0.0

        client.newCall(tickerReq).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val json = JSONObject(bodyStr)
                currentPrice = json.optDouble("lastPrice", 0.0)
                open24h = json.optDouble("openPrice", 0.0)
                high24h = json.optDouble("highPrice", 0.0)
                low24h = json.optDouble("lowPrice", 0.0)
                changePct = json.optDouble("priceChangePercent", 0.0)
                vol24h = json.optDouble("volume", 0.0)
                bid = json.optDouble("bidPrice", currentPrice)
                ask = json.optDouble("askPrice", currentPrice)
            }
        }

        // Fetch candles for each timeframe
        for (tf in timeframes) {
            val limit = if (tf == primaryTimeframe) 60 else 30
            val klineReq = Request.Builder()
                .url("$baseUrl/api/v3/klines?symbol=$symbol&interval=${tf.binanceInterval}&limit=$limit")
                .build()

            client.newCall(klineReq).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val array = JSONArray(bodyStr)
                    val candles = mutableListOf<Candle>()
                    for (i in 0 until array.length()) {
                        val item = array.getJSONArray(i)
                        val openTime = item.getLong(0)
                        val open = item.getString(1).toDouble()
                        val high = item.getString(2).toDouble()
                        val low = item.getString(3).toDouble()
                        val close = item.getString(4).toDouble()
                        val volume = item.getString(5).toDouble()

                        candles.add(
                            Candle(
                                timestamp = openTime,
                                open = open,
                                high = high,
                                low = low,
                                close = close,
                                volume = volume,
                                isClosed = (i < array.length() - 1)
                            )
                        )
                    }
                    val normalized = validateAndDeduplicate(candles)
                    candleMap[tf] = normalized
                    if (currentPrice == 0.0 && normalized.isNotEmpty()) {
                        currentPrice = normalized.last().close
                    }
                }
            }
        }

        val now = System.currentTimeMillis()
        val latestCandleTime = candleMap[Timeframe.M5]?.lastOrNull()?.timestamp ?: now
        val isStale = (now - latestCandleTime) > (15 * 60 * 1000) // Older than 15 mins for M5 is stale

        val status = when {
            candleMap.isEmpty() || currentPrice <= 0.0 -> DataStatus.UNAVAILABLE
            isStale -> DataStatus.STALE
            else -> DataStatus.LIVE
        }

        val spread = (ask - bid).coerceAtLeast(0.0)

        return MarketSnapshot(
            instrument = instrument,
            currentPrice = currentPrice,
            open24h = open24h,
            high24h = high24h,
            low24h = low24h,
            change24hPercent = changePct,
            volume24h = vol24h,
            bid = bid,
            ask = ask,
            spread = spread,
            candlesByTimeframe = candleMap,
            dataProvider = "Binance Public REST API",
            lastUpdateTimestamp = now,
            status = status,
            statusMessage = if (status == DataStatus.STALE) "STALE DATA - SIGNAL GENERATION DISABLED" else null
        )
    }

    private fun fetchGoldSnapshot(
        instrument: Instrument,
        primaryTimeframe: Timeframe
    ): MarketSnapshot {
        val now = System.currentTimeMillis()
        val timeframes = listOf(Timeframe.H4, Timeframe.H1, Timeframe.M15, Timeframe.M5)
        val candleMap = mutableMapOf<Timeframe, List<Candle>>()

        // Try Yahoo Finance v8 chart for Gold Spot / Futures (GC=F)
        val symbolsToTry = listOf("GC=F", "XAUUSD=X")
        var currentPrice = 0.0
        var open24h = 0.0
        var high24h = 0.0
        var low24h = 0.0
        var vol24h = 0.0
        var foundLiveCandles = false

        for (sym in symbolsToTry) {
            try {
                val url = "https://query1.finance.yahoo.com/v8/finance/chart/$sym?interval=15m&range=5d"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val root = JSONObject(body)
                        val chart = root.getJSONObject("chart")
                        val result = chart.getJSONArray("result").getJSONObject(0)
                        val meta = result.getJSONObject("meta")
                        currentPrice = meta.optDouble("regularMarketPrice", 0.0)
                        open24h = meta.optDouble("chartPreviousClose", currentPrice)

                        val timestamps = result.getJSONArray("timestamp")
                        val indicators = result.getJSONObject("indicators")
                        val quote = indicators.getJSONArray("quote").getJSONObject(0)
                        val opens = quote.getJSONArray("open")
                        val highs = quote.getJSONArray("high")
                        val lows = quote.getJSONArray("low")
                        val closes = quote.getJSONArray("close")
                        val volumes = quote.optJSONArray("volume")

                        val raw15m = mutableListOf<Candle>()
                        for (i in 0 until timestamps.length()) {
                            if (opens.isNull(i) || closes.isNull(i)) continue
                            val t = timestamps.getLong(i) * 1000
                            val o = opens.getDouble(i)
                            val h = highs.getDouble(i)
                            val l = lows.getDouble(i)
                            val c = closes.getDouble(i)
                            val v = if (volumes != null && !volumes.isNull(i)) volumes.getDouble(i) else 100.0

                            raw15m.add(Candle(t, o, h, l, c, v, i < timestamps.length() - 1))
                        }

                        if (raw15m.isNotEmpty()) {
                            foundLiveCandles = true
                            candleMap[Timeframe.M15] = validateAndDeduplicate(raw15m.takeLast(60))
                            candleMap[Timeframe.H1] = aggregateCandles(raw15m, 4).takeLast(40)
                            candleMap[Timeframe.H4] = aggregateCandles(raw15m, 16).takeLast(30)
                            candleMap[Timeframe.M5] = interpolateM5(raw15m).takeLast(50)

                            high24h = raw15m.takeLast(30).maxOf { it.high }
                            low24h = raw15m.takeLast(30).minOf { it.low }
                            vol24h = raw15m.takeLast(30).sumOf { it.volume }
                            break
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // If Yahoo was rate-limited or closed, check alternative Gold backing feed: Binance PAXGUSDT (1 PAXG = 1 Fine Troy Ounce Gold)
        if (!foundLiveCandles) {
            try {
                val paxgSnapshot = fetchBinanceSnapshot(
                    instrument,
                    primaryTimeframe,
                    overrideSymbol = "PAXGUSDT"
                )
                if (paxgSnapshot.status == DataStatus.LIVE && paxgSnapshot.currentPrice > 1000.0) {
                    return paxgSnapshot.copy(
                        instrument = instrument,
                        dataProvider = "Binance PAXG/USDT (1:1 Physical Gold Backed Spot Feed)"
                    )
                }
            } catch (_: Exception) {}
        }

        if (currentPrice <= 0.0 && candleMap.isEmpty()) {
            return MarketSnapshot(
                instrument = instrument,
                currentPrice = 0.0,
                open24h = 0.0,
                high24h = 0.0,
                low24h = 0.0,
                change24hPercent = 0.0,
                volume24h = 0.0,
                bid = 0.0,
                ask = 0.0,
                spread = 0.0,
                candlesByTimeframe = emptyMap(),
                dataProvider = "Global Spot Gold / Yahoo Finance",
                lastUpdateTimestamp = now,
                status = DataStatus.UNAVAILABLE,
                statusMessage = "MARKET DATA UNAVAILABLE: Unable to reach real-time Gold spot feeds. Check network or weekend market hours."
            )
        }

        val changePct = if (open24h > 0) ((currentPrice - open24h) / open24h) * 100.0 else 0.0
        val bid = currentPrice - 0.25
        val ask = currentPrice + 0.25

        return MarketSnapshot(
            instrument = instrument,
            currentPrice = currentPrice,
            open24h = open24h,
            high24h = high24h,
            low24h = low24h,
            change24hPercent = changePct,
            volume24h = vol24h,
            bid = bid,
            ask = ask,
            spread = 0.50,
            candlesByTimeframe = candleMap,
            dataProvider = "Yahoo Finance Gold (GC=F) / Global Feed",
            lastUpdateTimestamp = now,
            status = DataStatus.LIVE
        )
    }

    private fun validateAndDeduplicate(candles: List<Candle>): List<Candle> {
        val sorted = candles.sortedBy { it.timestamp }
        val result = mutableListOf<Candle>()
        for (c in sorted) {
            if (result.isEmpty() || result.last().timestamp != c.timestamp) {
                // Ensure high >= low and bounds
                val validHigh = maxOf(c.high, c.open, c.close)
                val validLow = minOf(c.low, c.open, c.close)
                result.add(c.copy(high = validHigh, low = validLow))
            }
        }
        return result
    }

    private fun aggregateCandles(candles: List<Candle>, groupSize: Int): List<Candle> {
        return candles.chunked(groupSize).mapNotNull { chunk ->
            if (chunk.isEmpty()) null
            else {
                Candle(
                    timestamp = chunk.first().timestamp,
                    open = chunk.first().open,
                    high = chunk.maxOf { it.high },
                    low = chunk.minOf { it.low },
                    close = chunk.last().close,
                    volume = chunk.sumOf { it.volume },
                    isClosed = chunk.last().isClosed
                )
            }
        }
    }

    private fun interpolateM5(candles15m: List<Candle>): List<Candle> {
        val result = mutableListOf<Candle>()
        for (c in candles15m) {
            val step = (c.close - c.open) / 3.0
            val subDuration = 5 * 60 * 1000L
            val vSub = c.volume / 3.0

            val c1Open = c.open
            val c1Close = c.open + step
            val c1High = maxOf(c1Open, c1Close, (c.high + c1Open) / 2)
            val c1Low = minOf(c1Open, c1Close, (c.low + c1Open) / 2)
            result.add(Candle(c.timestamp, c1Open, c1High, c1Low, c1Close, vSub, true))

            val c2Open = c1Close
            val c2Close = c1Close + step
            val c2High = maxOf(c2Open, c2Close, c.high)
            val c2Low = minOf(c2Open, c2Close, c.low)
            result.add(Candle(c.timestamp + subDuration, c2Open, c2High, c2Low, c2Close, vSub, true))

            val c3Open = c2Close
            val c3Close = c.close
            val c3High = maxOf(c3Open, c3Close)
            val c3Low = minOf(c3Open, c3Close)
            result.add(Candle(c.timestamp + subDuration * 2, c3Open, c3High, c3Low, c3Close, vSub, c.isClosed))
        }
        return result
    }
}
