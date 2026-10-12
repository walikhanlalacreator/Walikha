package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DataStatus
import com.example.data.model.Instrument
import com.example.data.model.RiskParameters
import com.example.data.model.SignalRecord
import com.example.data.model.Timeframe
import com.example.data.model.UserAlert
import com.example.data.model.UserProfile
import com.example.data.model.WebSocketConnectionState
import com.example.data.repository.AuthRepository
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.InstrumentAnalysisBundle
import com.example.data.repository.MarketRepository
import com.example.data.remote.MarketSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

enum class NavTab(val label: String) {
    DASHBOARD("Markets"),
    TERMINAL("Analysis"),
    AI_INTEL("AI Intel"),
    RISK("Risk Calc"),
    BACKTEST("Backtest"),
    JOURNAL("Journal")
}

class TradingViewModel(
    val marketRepository: MarketRepository,
    val firestoreRepository: FirestoreRepository,
    val authRepository: AuthRepository
) : ViewModel() {

    val selectedInstrument: StateFlow<Instrument> = marketRepository.currentInstrument
    val currentBundle: StateFlow<InstrumentAnalysisBundle?> = marketRepository.currentBundle
    val allSnapshots: StateFlow<Map<Instrument, MarketSnapshot>> = marketRepository.allInstrumentsSnapshots
    val isMarketLoading: StateFlow<Boolean> = marketRepository.isLoading
    val marketError: StateFlow<String?> = marketRepository.errorMessage

    // Real-time WebSocket connection state
    val webSocketConnectionState: StateFlow<WebSocketConnectionState> = marketRepository.webSocketConnectionState
    val globalLastUpdateTimestamp: StateFlow<Long> = marketRepository.globalLastUpdateTimestamp
    val lastUpdateByInstrument: StateFlow<Map<Instrument, Long>> = marketRepository.lastUpdateByInstrument

    private val _activeTab = MutableStateFlow(NavTab.DASHBOARD)
    val activeTab: StateFlow<NavTab> = _activeTab.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.M15)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    // Risk Management State
    private val _riskParams = MutableStateFlow(RiskParameters())
    val riskParams: StateFlow<RiskParameters> = _riskParams.asStateFlow()

    val riskCalculation = MutableStateFlow(marketRepository.calculateRisk(_riskParams.value))

    // AI States
    private val _flashScanText = MutableStateFlow<String?>(null)
    val flashScanText: StateFlow<String?> = _flashScanText.asStateFlow()
    val isScanningFlash = MutableStateFlow(false)

    private val _macroNewsText = MutableStateFlow<String?>(null)
    val macroNewsText: StateFlow<String?> = _macroNewsText.asStateFlow()
    val isSearchingMacro = MutableStateFlow(false)

    private val _chartVisionText = MutableStateFlow<String?>(null)
    val chartVisionText: StateFlow<String?> = _chartVisionText.asStateFlow()
    val isAnalyzingImage = MutableStateFlow(false)

    val userMessage = MutableStateFlow<String?>(null)

    // TTS engine
    private var tts: TextToSpeech? = null
    val isTtsSpeaking = MutableStateFlow(false)

    // Firestore streams
    val userProfile: StateFlow<UserProfile?> = firestoreRepository.observeUserProfile()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isGuestMode: StateFlow<Boolean> = authRepository.isGuestMode
    private val _guestSignals = MutableStateFlow<List<SignalRecord>>(emptyList())

    val savedSignals: StateFlow<List<SignalRecord>> = combine(
        authRepository.isGuestMode,
        _guestSignals,
        firestoreRepository.observeSignals().catch { emit(emptyList()) }
    ) { isGuest, guestList, cloudList ->
        if (isGuest) guestList else cloudList
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userAlerts: StateFlow<List<UserAlert>> = firestoreRepository.observeAlerts()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Start real-time WebSocket streaming for XAU/USD, BTC/USD, ETH/USD, SOL/USD, XRP/USD
        marketRepository.startWebSocketStream(viewModelScope)

        viewModelScope.launch {
            marketRepository.refreshAllInstruments()
        }

        // Sync risk params when instrument or bundle changes
        viewModelScope.launch {
            currentBundle.collect { bundle ->
                if (bundle != null && bundle.signal.currentPrice > 0) {
                    val p = _riskParams.value
                    val entry = if (bundle.signal.entryPrice > 0) bundle.signal.entryPrice else bundle.signal.currentPrice
                    val sl = if (bundle.signal.stopLoss > 0) bundle.signal.stopLoss else entry * 0.995
                    val tp1 = if (bundle.signal.takeProfit1 > 0) bundle.signal.takeProfit1 else entry * 1.01
                    val tp2 = if (bundle.signal.takeProfit2 > 0) bundle.signal.takeProfit2 else entry * 1.02
                    val tp3 = if (bundle.signal.takeProfit3 > 0) bundle.signal.takeProfit3 else entry * 1.03

                    val updated = p.copy(
                        instrument = bundle.instrument,
                        contractSize = bundle.instrument.defaultContractSize,
                        tickSize = bundle.instrument.defaultTickSize,
                        tickValue = bundle.instrument.defaultTickValue,
                        entryPrice = entry,
                        stopLossPrice = sl,
                        takeProfit1 = tp1,
                        takeProfit2 = tp2,
                        takeProfit3 = tp3
                    )
                    _riskParams.value = updated
                    riskCalculation.value = marketRepository.calculateRisk(updated)
                }
            }
        }
    }

    fun setTab(tab: NavTab) {
        _activeTab.value = tab
    }

    fun selectInstrument(instrument: Instrument) {
        marketRepository.selectInstrument(instrument)
        viewModelScope.launch {
            marketRepository.refreshCurrentInstrument(_selectedTimeframe.value)
        }
    }

    fun setTimeframe(tf: Timeframe) {
        _selectedTimeframe.value = tf
        viewModelScope.launch {
            marketRepository.refreshCurrentInstrument(tf)
        }
    }

    fun refreshMarkets() {
        viewModelScope.launch {
            marketRepository.refreshAllInstruments()
            if (marketRepository.webSocketConnectionState.value != WebSocketConnectionState.CONNECTED) {
                marketRepository.reconnectWebSocket()
            }
        }
    }

    fun reconnectWebSocket() {
        marketRepository.reconnectWebSocket()
    }

    fun toggleWebSocket(enabled: Boolean) {
        marketRepository.toggleWebSocket(enabled)
    }

    fun updateRiskParameters(params: RiskParameters) {
        _riskParams.value = params
        riskCalculation.value = marketRepository.calculateRisk(params)
    }

    fun saveRiskProfile(balance: Double, riskPct: Double, maxLoss: Double) {
        val current = _riskParams.value
        val updated = current.copy(
            accountBalance = balance,
            riskPercentage = riskPct,
            maxDailyLoss = maxLoss
        )
        _riskParams.value = updated
        riskCalculation.value = marketRepository.calculateRisk(updated)

        val user = authRepository.currentUser.value
        if (user != null) {
            viewModelScope.launch {
                val profile = UserProfile(
                    userId = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    accountBalance = balance,
                    defaultRiskPercent = riskPct,
                    maxDailyLoss = maxLoss
                )
                firestoreRepository.saveUserProfile(profile)
                userMessage.value = "Risk profile saved to cloud."
            }
        } else {
            userMessage.value = "Risk profile applied to local session."
        }
    }

    fun saveCurrentSignalToJournal(notes: String = "") {
        val bundle = currentBundle.value ?: return
        val signal = bundle.signal
        val user = authRepository.currentUser.value

        if (user != null) {
            viewModelScope.launch {
                val record = SignalRecord(
                    userId = user.uid,
                    symbol = bundle.instrument.displaySymbol,
                    direction = signal.type.name,
                    entry = signal.entryPrice,
                    stopLoss = signal.stopLoss,
                    takeProfit1 = signal.takeProfit1,
                    takeProfit2 = signal.takeProfit2,
                    takeProfit3 = signal.takeProfit3,
                    riskReward = signal.riskRewardRatio1,
                    setupQualityScore = signal.setupQualityScore,
                    profileShape = bundle.marketProfile.shape.name,
                    marketRegime = bundle.marketProfile.shape.label,
                    invalidationConditions = signal.invalidationConditions,
                    outcome = "PENDING",
                    journalNotes = notes,
                    dataSource = bundle.snapshot.dataProvider
                )
                val result = firestoreRepository.saveSignal(record)
                if (result.isSuccess) {
                    userMessage.value = "Signal journaled successfully to cloud."
                } else {
                    userMessage.value = "Failed to save signal: ${result.exceptionOrNull()?.message}"
                }
            }
        } else {
            // Local guest journal
            val record = SignalRecord(
                id = "guest_${System.currentTimeMillis()}",
                userId = "guest_local",
                symbol = bundle.instrument.displaySymbol,
                direction = signal.type.name,
                entry = signal.entryPrice,
                stopLoss = signal.stopLoss,
                takeProfit1 = signal.takeProfit1,
                takeProfit2 = signal.takeProfit2,
                takeProfit3 = signal.takeProfit3,
                riskReward = signal.riskRewardRatio1,
                setupQualityScore = signal.setupQualityScore,
                profileShape = bundle.marketProfile.shape.name,
                marketRegime = bundle.marketProfile.shape.label,
                invalidationConditions = signal.invalidationConditions,
                outcome = "PENDING",
                journalNotes = notes,
                dataSource = bundle.snapshot.dataProvider
            )
            _guestSignals.value = listOf(record) + _guestSignals.value
            userMessage.value = "Signal saved to local journal (Guest Mode)."
        }
    }

    fun updateSignalOutcome(signalId: String, outcome: String, notes: String) {
        if (authRepository.isGuestMode.value) {
            _guestSignals.value = _guestSignals.value.map { item ->
                if (item.id == signalId) {
                    item.copy(outcome = outcome, journalNotes = notes)
                } else item
            }
            userMessage.value = "Signal updated in local journal."
        } else {
            viewModelScope.launch {
                val result = firestoreRepository.updateSignalOutcome(signalId, outcome, notes)
                if (result.isSuccess) {
                    userMessage.value = "Signal outcome updated to $outcome in cloud."
                } else {
                    userMessage.value = "Failed to update signal: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun runAiDeepThinking() {
        viewModelScope.launch {
            marketRepository.runAiDeepThinkingAnalysis()
        }
    }

    fun runFlashScan() {
        val bundle = currentBundle.value ?: return
        isScanningFlash.value = true
        _flashScanText.value = null
        viewModelScope.launch {
            val res = marketRepository.runFlashLiteScan(
                bundle.instrument,
                bundle.signal.currentPrice,
                bundle.signal.type.name
            )
            _flashScanText.value = res
            isScanningFlash.value = false
        }
    }

    fun runMacroSearch() {
        val bundle = currentBundle.value ?: return
        isSearchingMacro.value = true
        _macroNewsText.value = null
        viewModelScope.launch {
            val res = marketRepository.fetchSearchGroundedMacroContext(bundle.instrument)
            _macroNewsText.value = res
            isSearchingMacro.value = false
        }
    }

    fun analyzeChartScreenshot(bitmap: Bitmap, userNotes: String = "") {
        isAnalyzingImage.value = true
        _chartVisionText.value = null
        viewModelScope.launch {
            val res = marketRepository.analyzeChartScreenshot(bitmap, userNotes)
            _chartVisionText.value = res
            isAnalyzingImage.value = false
        }
    }

    fun runBacktest() {
        marketRepository.runBacktestOnCurrent()
    }

    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                }
            }
        }
    }

    fun speakSignalSummary(context: Context) {
        val bundle = currentBundle.value ?: return
        initTts(context)
        val text = buildString {
            append("Gold Profile analysis for ${bundle.instrument.assetName}. ")
            append("Current price is ${"%.2f".format(bundle.signal.currentPrice)}. ")
            append("Deterministic decision is ${bundle.signal.type.name}. ")
            if (bundle.signal.type != com.example.data.model.SignalType.NO_TRADE) {
                append("Proposed entry at ${"%.2f".format(bundle.signal.entryPrice)}. ")
                append("Stop loss at ${"%.2f".format(bundle.signal.stopLoss)}. ")
                append("Take profit target at ${"%.2f".format(bundle.signal.takeProfit1)}. ")
            }
            append("Setup Quality Score is ${bundle.signal.setupQualityScore} out of 100. ")
            append("Profile shape is classified as ${bundle.marketProfile.shape.label}. ")
        }

        isTtsSpeaking.value = true
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SignalTTS")
    }

    fun stopTts() {
        tts?.stop()
        isTtsSpeaking.value = false
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}
