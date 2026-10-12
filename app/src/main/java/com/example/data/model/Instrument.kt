package com.example.data.model

enum class Instrument(
    val id: String,
    val displaySymbol: String,
    val assetName: String,
    val quoteCurrency: String,
    val isCrypto: Boolean,
    val defaultContractSize: Double, // e.g. 100 oz for Gold, 1.0 unit for BTC
    val defaultTickSize: Double,
    val defaultTickValue: Double,
    val volumeDescription: String,
    val defaultFeeRatePercent: Double,
    val binanceSymbol: String? = null,
    val yahooSymbol: String? = null
) {
    XAU_USD(
        id = "XAU_USD",
        displaySymbol = "XAU/USD",
        assetName = "Gold Spot",
        quoteCurrency = "USD",
        isCrypto = false,
        defaultContractSize = 100.0, // 1 Standard Lot = 100 troy oz
        defaultTickSize = 0.05,
        defaultTickValue = 5.0,
        volumeDescription = "Broker Tick Volume (Not Global Exchange Total)",
        defaultFeeRatePercent = 0.02,
        yahooSymbol = "GC=F"
    ),
    BTC_USD(
        id = "BTC_USD",
        displaySymbol = "BTC/USDT",
        assetName = "Bitcoin",
        quoteCurrency = "USDT (Spot Exchange)",
        isCrypto = true,
        defaultContractSize = 1.0,
        defaultTickSize = 0.1,
        defaultTickValue = 0.1,
        volumeDescription = "Exchange Traded Volume (Binance Spot)",
        defaultFeeRatePercent = 0.075,
        binanceSymbol = "BTCUSDT"
    ),
    ETH_USD(
        id = "ETH_USD",
        displaySymbol = "ETH/USDT",
        assetName = "Ethereum",
        quoteCurrency = "USDT (Spot Exchange)",
        isCrypto = true,
        defaultContractSize = 1.0,
        defaultTickSize = 0.01,
        defaultTickValue = 0.01,
        volumeDescription = "Exchange Traded Volume (Binance Spot)",
        defaultFeeRatePercent = 0.075,
        binanceSymbol = "ETHUSDT"
    ),
    SOL_USD(
        id = "SOL_USD",
        displaySymbol = "SOL/USDT",
        assetName = "Solana",
        quoteCurrency = "USDT (Spot Exchange)",
        isCrypto = true,
        defaultContractSize = 1.0,
        defaultTickSize = 0.01,
        defaultTickValue = 0.01,
        volumeDescription = "Exchange Traded Volume (Binance Spot)",
        defaultFeeRatePercent = 0.075,
        binanceSymbol = "SOLUSDT"
    ),
    XRP_USD(
        id = "XRP_USD",
        displaySymbol = "XRP/USDT",
        assetName = "XRP",
        quoteCurrency = "USDT (Spot Exchange)",
        isCrypto = true,
        defaultContractSize = 1.0,
        defaultTickSize = 0.0001,
        defaultTickValue = 0.0001,
        volumeDescription = "Exchange Traded Volume (Binance Spot)",
        defaultFeeRatePercent = 0.075,
        binanceSymbol = "XRPUSDT"
    );

    companion object {
        fun fromId(id: String): Instrument = entries.firstOrNull { it.id == id } ?: XAU_USD
    }
}
