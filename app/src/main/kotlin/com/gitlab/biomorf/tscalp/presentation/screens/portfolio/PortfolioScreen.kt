package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.gitlab.biomorf.tscalp.presentation.components.ApiNotInitializedCard
import com.gitlab.biomorf.tscalp.presentation.components.StatusEventEffect
import com.gitlab.biomorf.tscalp.presentation.components.StatusSnackbarHost

// 2c-fixation (U2 Pass-3): body ~91 lines > 60 (LongMethod), cycom 16 > 14 (CyclomaticComplexMethod).
// Split forwards ~10 callbacks / 6 state fields (analog of OrdersScrollableContent); behaviour 1:1.
@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(uiState.isLoading) {
        isRefreshing = uiState.isLoading
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Портфель") },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isLoading && uiState.isApiInitialized,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        if (!uiState.isApiInitialized) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                ApiNotInitializedCard()
            }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (uiState.isLoading && !isRefreshing) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                StatusEventEffect(snackbarHostState = snackbarHostState, status = viewModel.status)

                val allKeys =
                    uiState.sheet.accounts.keys.sortedWith(
                        compareBy({ it.brokerName.ordinal }, { it.accountId }),
                    )

                if (allKeys.isEmpty() && !uiState.isLoading) {
                    EmptyPortfolioCard()
                } else {
                    for (key in allKeys) {
                        val account = uiState.sheet.accounts[key] ?: continue
                        PortfolioAccountBlock(
                            accountKey = key,
                            accountPortfolio = account,
                            prices = uiState.prices,
                            priceChangePercents = uiState.priceChangePercents,
                            tradingStatuses = uiState.tradingStatuses,
                            sandboxMode = uiState.sandboxMode,
                            onPayIn = { viewModel.payInSandbox(key.accountId) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            StatusSnackbarHost(snackbarHostState = snackbarHostState)
        }
    }
}
