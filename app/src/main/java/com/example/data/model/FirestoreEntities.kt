package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val accountBalance: Double = 10000.0,
    val accountCurrency: String = "USD",
    val defaultRiskPercent: Double = 0.5,
    val maxDailyLoss: Double = 300.0,
    val maxOpenRisk: Double = 500.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "email" to email,
        "displayName" to displayName,
        "accountBalance" to accountBalance,
        "accountCurrency" to accountCurrency,
        "defaultRiskPercent" to defaultRiskPercent,
        "maxDailyLoss" to maxDailyLoss,
        "maxOpenRisk" to maxOpenRisk
    ).filterValues { it != null }
}

@IgnoreExtraProperties
data class SignalRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val direction: String = "",
    val entry: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit1: Double = 0.0,
    val takeProfit2: Double = 0.0,
    val takeProfit3: Double = 0.0,
    val riskReward: Double = 0.0,
    val setupQualityScore: Int = 0,
    val profileShape: String = "",
    val marketRegime: String = "",
    val invalidationConditions: String = "",
    val outcome: String = "PENDING", // PENDING, TP1_HIT, TP2_HIT, TP3_HIT, SL_HIT, INVALIDATED
    val journalNotes: String = "",
    val dataSource: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "symbol" to symbol,
        "direction" to direction,
        "entry" to entry,
        "stopLoss" to stopLoss,
        "takeProfit1" to takeProfit1,
        "takeProfit2" to takeProfit2,
        "takeProfit3" to takeProfit3,
        "riskReward" to riskReward,
        "setupQualityScore" to setupQualityScore,
        "profileShape" to profileShape,
        "marketRegime" to marketRegime,
        "invalidationConditions" to invalidationConditions,
        "outcome" to outcome,
        "journalNotes" to journalNotes,
        "dataSource" to dataSource
    ).filterValues { it != null }
}

@IgnoreExtraProperties
data class UserAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val alertType: String = "",
    val title: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "symbol" to symbol,
        "alertType" to alertType,
        "title" to title,
        "message" to message,
        "isRead" to isRead
    ).filterValues { it != null }
}
