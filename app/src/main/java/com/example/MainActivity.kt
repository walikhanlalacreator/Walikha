package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthState
import com.example.data.repository.FirestoreRepository
import com.example.data.repository.MarketRepository
import com.example.ui.screens.AiIntelScreen
import com.example.ui.screens.AnalysisDetailScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BacktestScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.RiskCalculatorScreen
import com.example.ui.screens.UserSessionHeader
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeutralGray
import com.example.ui.theme.TerminalBackground
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.NavTab
import com.example.ui.viewmodel.TradingViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                GoldProfileApp()
            }
        }
    }
}

@Composable
fun GoldProfileApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val authRepo = remember { AuthRepository(context) }
    val authState by authRepo.authState.collectAsState()

    LaunchedEffect(Unit) {
        authRepo.attemptAutoSignIn(
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    if (authState is AuthState.Unauthenticated) {
        AuthScreen(
            authRepository = authRepo,
            onAuthSuccess = {},
            onGuestSelected = {}
        )
    } else {
        val firestoreRepo = remember { FirestoreRepository(context) }
        val marketRepo = remember { MarketRepository() }
        val viewModel = remember { TradingViewModel(marketRepo, firestoreRepo, authRepo) }

        MainTerminalContent(
            viewModel = viewModel,
            authRepo = authRepo,
            authState = authState
        )
    }
}

@Composable
fun MainTerminalContent(
    viewModel: TradingViewModel,
    authRepo: AuthRepository,
    authState: AuthState
) {
    val activeTab by viewModel.activeTab.collectAsState()
    val scope = rememberCoroutineScope()

    // BackHandler: Return to Dashboard if on a sub-screen
    BackHandler(enabled = activeTab != NavTab.DASHBOARD) {
        viewModel.setTab(NavTab.DASHBOARD)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = TerminalBackground,
        bottomBar = {
            NavigationBar(
                containerColor = TerminalSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bottom_nav_bar")
            ) {
                val items = listOf(
                    Triple(NavTab.DASHBOARD, "Markets", Icons.Default.CandlestickChart),
                    Triple(NavTab.TERMINAL, "Analysis", Icons.Default.Analytics),
                    Triple(NavTab.AI_INTEL, "AI Intel", Icons.Default.Psychology),
                    Triple(NavTab.RISK, "Risk", Icons.Default.Calculate),
                    Triple(NavTab.BACKTEST, "Backtest", Icons.Default.History),
                    Triple(NavTab.JOURNAL, "Journal", Icons.Default.Book)
                )

                items.forEach { (tab, label, icon) ->
                    val isSelected = (activeTab == tab)
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { viewModel.setTab(tab) },
                        icon = {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (isSelected) GoldPrimary else NeutralGray
                            )
                        },
                        label = {
                            Text(
                                label,
                                color = if (isSelected) GoldPrimary else TextSecondary,
                                fontSize = 10.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = GoldPrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // User Session Header
            UserSessionHeader(
                authState = authState,
                authRepo = authRepo,
                onSignOut = {
                    authRepo.signOut(scope) {}
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    NavTab.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                    NavTab.TERMINAL -> AnalysisDetailScreen(viewModel = viewModel)
                    NavTab.AI_INTEL -> AiIntelScreen(viewModel = viewModel)
                    NavTab.RISK -> RiskCalculatorScreen(viewModel = viewModel)
                    NavTab.BACKTEST -> BacktestScreen(viewModel = viewModel)
                    NavTab.JOURNAL -> JournalScreen(viewModel = viewModel)
                }
            }
        }
    }
}
