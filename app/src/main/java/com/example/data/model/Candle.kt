package com.example.data.model

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val isClosed: Boolean = true
) {
    val isBullish: Boolean get() = close >= open
    val bodyHigh: Double get() = maxOf(open, close)
    val bodyLow: Double get() = minOf(open, close)
    val bodySize: Double get() = kotlin.math.abs(close - open)
    val upperWick: Double get() = high - bodyHigh
    val lowerWick: Double get() = bodyLow - low
    val totalRange: Double get() = high - low
}

enum class Timeframe(
    val label: String,
    val minutes: Int,
    val binanceInterval: String
) {
    H4("4H", 240, "4h"),
    H1("1H", 60, "1h"),
    M15("15M", 15, "15m"),
    M5("5M", 5, "5m");

    companion object {
        fun fromLabel(label: String): Timeframe = entries.firstOrNull { it.label == label } ?: M15
    }
}

enum class DataStatus {
    LIVE,
    STALE,
    UNAVAILABLE,
    DEMO_LABELED
}

enum class WebSocketConnectionState(val displayName: String) {
    CONNECTING("Connecting"),
    CONNECTED("WebSocket Live"),
    RECONNECTING("Reconnecting"),
    FALLBACK_REST("REST Fallback Active"),
    DISCONNECTED("Disconnected")
}
