package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthState
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NeutralGray
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.google.firebase.auth.FirebaseUser

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit = {},
    onGuestSelected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .testTag("auth_card"),
            colors = CardDefaults.cardColors(containerColor = TerminalSurface),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder)
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Institutional Brand Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(GoldPrimary.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, GoldPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Security Shield",
                        tint = GoldPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "GOLD PROFILE AI",
                    color = GoldPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = 1.2.sp
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Institutional Market Profile & Order Flow Terminal",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Select your access mode to enter the trading terminal:",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                errorMessage?.let { err ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BearishRed.copy(0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, BearishRed.copy(0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = err,
                            color = BearishRed,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // OPTION 1: GOOGLE ACCOUNT LOGIN
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(GoldPrimary.copy(0.4f))
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Google Account Access",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(GoldPrimary.copy(0.18f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "RECOMMENDED",
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AuthFeatureRow("Real-time WebSocket streaming (PAXG, BTC, ETH, SOL, XRP)")
                            AuthFeatureRow("Cloud-synced Trade Journal backed across all devices")
                            AuthFeatureRow("Persistent risk profile & custom alert history in Firestore")
                        }

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                isLoading = true
                                errorMessage = null
                                val activity = context as? Activity
                                if (activity != null) {
                                    authRepository.signInWithGoogle(
                                        activity = activity,
                                        scope = scope,
                                        onSuccess = {
                                            isLoading = false
                                            onAuthSuccess()
                                        },
                                        onError = { err ->
                                            isLoading = false
                                            errorMessage = err
                                        },
                                        onCancelled = {
                                            isLoading = false
                                        }
                                    )
                                } else {
                                    isLoading = false
                                    errorMessage = "Activity context unavailable"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("google_sign_in_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = TerminalBackground,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = TerminalBackground,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Sign in with Google",
                                        color = TerminalBackground,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // DIVIDER
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = TerminalBorder
                    )
                    Text(
                        text = " OR ",
                        color = NeutralGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = TerminalBorder
                    )
                }
                Spacer(Modifier.height(16.dp))

                // OPTION 2: GUEST MODE LOGIN
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TerminalSurfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = BlueAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Continue as Guest",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(BlueAccent.copy(0.18f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "INSTANT ACCESS",
                                    color = BlueAccent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AuthFeatureRow("Instant terminal entry — no Google account needed")
                            AuthFeatureRow("Full 24/7 WebSocket feeds & Market Profile engine")
                            AuthFeatureRow("Local trading journal & position risk calculations")
                            AuthFeatureRow("Upgrade & connect Google account anytime", isNote = true)
                        }

                        Spacer(Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                authRepository.signInAsGuest()
                                onGuestSelected()
                                onAuthSuccess()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("guest_sign_in_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(BlueAccent)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isLoading
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = BlueAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Explore as Guest Trader",
                                    color = BlueAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "You can seamlessly switch accounts or link Google at any time from the terminal header.",
                    color = TextTertiary,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(text: String, isNote: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isNote) NeutralGray else BullishGreen,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = if (isNote) NeutralGray else TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
fun UserSessionHeader(
    authState: AuthState,
    authRepo: AuthRepository,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSigningInGoogle by remember { mutableStateOf(false) }

    when (authState) {
        is AuthState.GoogleAuthenticated -> {
            val user = authState.user
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(TerminalSurfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, TerminalBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(BullishGreen.copy(0.2f), CircleShape)
                            .border(1.dp, BullishGreen.copy(0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (user.displayName ?: user.email ?: "U").take(1).uppercase(),
                            color = BullishGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = user.displayName ?: user.email ?: "Institutional Trader",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(BullishGreen, CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Google Account • Cloud Synced",
                                color = BullishGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(6.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder)
                    )
                ) {
                    Text("Sign Out", color = TextSecondary, fontSize = 9.sp)
                }
            }
        }
        is AuthState.GuestAuthenticated -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .background(TerminalSurfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(GoldPrimary.copy(0.18f), CircleShape)
                            .border(1.dp, GoldPrimary.copy(0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Guest Trader",
                            tint = GoldPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = authState.displayName,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GoldPrimary, CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Guest Mode • Local Session",
                                color = GoldPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            val activity = context as? Activity
                            if (activity != null) {
                                isSigningInGoogle = true
                                authRepo.signInWithGoogle(
                                    activity = activity,
                                    scope = scope,
                                    onSuccess = { isSigningInGoogle = false },
                                    onError = { isSigningInGoogle = false },
                                    onCancelled = { isSigningInGoogle = false }
                                )
                            }
                        },
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("upgrade_google_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(6.dp),
                        enabled = !isSigningInGoogle
                    ) {
                        if (isSigningInGoogle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = TerminalBackground,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Login,
                                    contentDescription = null,
                                    tint = TerminalBackground,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Connect Google",
                                    color = TerminalBackground,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("sign_out_button"),
                        shape = RoundedCornerShape(6.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder)
                        )
                    ) {
                        Text("Exit", color = TextSecondary, fontSize = 9.sp)
                    }
                }
            }
        }
        AuthState.Unauthenticated -> {
            // Nothing to display when completely unauthenticated
        }
    }
}

// Backward-compatible overload for existing calls that pass FirebaseUser?
@Composable
fun UserSessionHeader(
    user: FirebaseUser?,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (user == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalSurfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, TerminalBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(BullishGreen.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (user.displayName ?: user.email ?: "U").take(1).uppercase(),
                    color = BullishGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = user.displayName ?: user.email ?: "Authenticated Trader",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                Text(
                    text = "Cloud DB: Connected",
                    color = BullishGreen,
                    fontSize = 9.sp
                )
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier
                .height(28.dp)
                .testTag("sign_out_button"),
            shape = RoundedCornerShape(6.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TerminalBorder))
        ) {
            Text("Sign Out", color = TextSecondary, fontSize = 9.sp)
        }
    }
}
