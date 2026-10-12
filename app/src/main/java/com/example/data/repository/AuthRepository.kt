package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class GoogleAuthenticated(val user: FirebaseUser) : AuthState()
    data class GuestAuthenticated(
        val guestId: String = "guest_trader",
        val displayName: String = "Guest Trader"
    ) : AuthState()
}

class AuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val credentialManager: CredentialManager = CredentialManager.create(context)
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val keyGuestMode = "is_guest_mode"

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isGuestMode = MutableStateFlow<Boolean>(
        auth.currentUser == null && prefs.getBoolean(keyGuestMode, false)
    )
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(
        when {
            auth.currentUser != null -> AuthState.GoogleAuthenticated(auth.currentUser!!)
            prefs.getBoolean(keyGuestMode, false) -> AuthState.GuestAuthenticated()
            else -> AuthState.Unauthenticated
        }
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { fbAuth ->
        val user = fbAuth.currentUser
        _currentUser.value = user
        if (user != null) {
            _isGuestMode.value = false
            prefs.edit().putBoolean(keyGuestMode, false).apply()
            _authState.value = AuthState.GoogleAuthenticated(user)
        } else {
            if (_isGuestMode.value) {
                _authState.value = AuthState.GuestAuthenticated()
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    fun signInAsGuest() {
        Log.i("Auth", "User logged in as Guest.")
        _isGuestMode.value = true
        prefs.edit().putBoolean(keyGuestMode, true).apply()
        _authState.value = AuthState.GuestAuthenticated()
    }

    fun attemptAutoSignIn(
        onAuthSuccess: () -> Unit = {},
        onUnauthenticated: () -> Unit = {},
        scope: CoroutineScope
    ) {
        if (auth.currentUser != null) {
            _isGuestMode.value = false
            _authState.value = AuthState.GoogleAuthenticated(auth.currentUser!!)
            onAuthSuccess()
            return
        }

        if (prefs.getBoolean(keyGuestMode, false)) {
            _isGuestMode.value = true
            _authState.value = AuthState.GuestAuthenticated()
            onAuthSuccess()
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    _isGuestMode.value = false
                    prefs.edit().putBoolean(keyGuestMode, false).apply()
                    if (user != null) {
                        _authState.value = AuthState.GoogleAuthenticated(user)
                    }
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    _isGuestMode.value = false
                    prefs.edit().putBoolean(keyGuestMode, false).apply()
                    if (user != null) {
                        _authState.value = AuthState.GoogleAuthenticated(user)
                    }
                    onSuccess()
                } else {
                    onError("Unexpected credential format received.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onCancelled()
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                onError(e.localizedMessage ?: "Sign in failed")
            }
        }
    }

    fun signOut(scope: CoroutineScope, onComplete: () -> Unit) {
        val hadGoogleSession = auth.currentUser != null
        _isGuestMode.value = false
        prefs.edit().putBoolean(keyGuestMode, false).apply()
        auth.signOut()
        _currentUser.value = null
        _authState.value = AuthState.Unauthenticated
        if (hadGoogleSession) {
            scope.launch {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.e("Auth", "Failed to clear credential state", e)
                } finally {
                    onComplete()
                }
            }
        } else {
            onComplete()
        }
    }
}
