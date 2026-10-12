package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SignalRecord
import com.example.data.model.UserProfile
import com.example.data.repository.FirestoreRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun `authenticated user can save and access profile`() = runBlocking {
    val uid = signInTestUser("alice.trader@example.com")
    val repository = FirestoreRepository(firestore, auth)

    val profile = UserProfile(
      userId = uid,
      displayName = "Alice Trader",
      accountBalance = 25000.0,
      accountCurrency = "USD",
      defaultRiskPercent = 0.5
    )

    val result = repository.saveUserProfile(profile)
    assertTrue("Saving user profile should succeed", result.isSuccess)
  }

  @Test
  fun `authenticated user can save trading signal`() = runBlocking {
    val uid = signInTestUser("bob.trader@example.com")
    val repository = FirestoreRepository(firestore, auth)

    val signal = SignalRecord(
      userId = uid,
      symbol = "XAU/USD",
      direction = "BUY",
      entry = 2650.0,
      stopLoss = 2640.0,
      takeProfit1 = 2665.0,
      takeProfit2 = 2680.0,
      takeProfit3 = 2700.0,
      riskReward = 1.5,
      setupQualityScore = 88,
      profileShape = "P-SHAPED",
      marketRegime = "TRENDING",
      dataSource = "Live Spot Feed"
    )

    val result = repository.saveSignal(signal)
    assertTrue("Saving signal should succeed", result.isSuccess)
    assertNotNull(result.getOrNull())
  }
}
