package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SignalRecord
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthModeTest : FirestoreEmulatorTestBase() {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUpDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDownDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAuthStateHierarchy() {
        val unauth: AuthState = AuthState.Unauthenticated
        assertTrue(unauth is AuthState.Unauthenticated)

        val guest: AuthState = AuthState.GuestAuthenticated("guest_123", "Guest Trader")
        assertTrue(guest is AuthState.GuestAuthenticated)
        assertEquals("guest_123", (guest as AuthState.GuestAuthenticated).guestId)
        assertEquals("Guest Trader", guest.displayName)
    }

    @Test
    fun testGuestSignInAndStateTransitions() = testScope.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authRepo = AuthRepository(context = context, auth = auth)

        // Trigger sign in as guest
        authRepo.signInAsGuest()

        assertTrue("Should be in guest mode", authRepo.isGuestMode.value)
        val state = authRepo.authState.value
        assertTrue("State should be GuestAuthenticated", state is AuthState.GuestAuthenticated)
        assertEquals("Guest Trader", (state as AuthState.GuestAuthenticated).displayName)

        // Sign out
        authRepo.signOut(testScope) {}
        testScheduler.advanceUntilIdle()

        assertFalse("Guest mode should be cleared after sign out", authRepo.isGuestMode.value)
        assertTrue("Auth state should return to Unauthenticated", authRepo.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun testGuestSignalModelIntegrity() {
        val guestSignal = SignalRecord(
            id = "guest_001",
            userId = "guest_trader",
            symbol = "XAU/USD",
            direction = "BUY",
            entry = 2735.0,
            stopLoss = 2725.0,
            takeProfit1 = 2745.0,
            takeProfit2 = 2755.0,
            takeProfit3 = 2770.0,
            riskReward = 2.0,
            setupQualityScore = 90,
            profileShape = "P-SHAPED",
            marketRegime = "TRENDING",
            outcome = "PENDING",
            journalNotes = "Institutional POC bounce in Guest session",
            dataSource = "Binance WebSocket (PAXG Gold 1:1 Spot)"
        )

        assertEquals("guest_001", guestSignal.id)
        assertEquals("guest_trader", guestSignal.userId)
        assertEquals("XAU/USD", guestSignal.symbol)
        assertEquals("PENDING", guestSignal.outcome)
        assertTrue(guestSignal.dataSource.contains("WebSocket"))
    }
}
