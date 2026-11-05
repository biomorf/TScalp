package com.gitlab.biomorf.tscalp.presentation.screens.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.presentation.components.AssetPositionCard
import com.gitlab.biomorf.tscalp.presentation.util.formatDomesticCurrency

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

                LaunchedEffect(uiState.statusMessage) {
                    uiState.statusMessage?.let { message ->
                        val visuals =
                            object : SnackbarVisuals {
                                override val message: String = message
                                override val actionLabel: String? = if (uiState.isError) "OK" else null
                                override val withDismissAction: Boolean = false
                                override val duration: SnackbarDuration =
                                    if (uiState.isError) {
                                        SnackbarDuration.Indefinite
                                    } else {
                                        SnackbarDuration.Short
                                    }
                            }
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(visuals)
                        viewModel.clearStatus()
                    }
                }

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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
            ) { data ->
                val label = data.visuals.actionLabel
                Snackbar(
                    containerColor =
                        if (uiState.isError) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                    contentColor =
                        if (uiState.isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                    action = {
                        if (label != null) {
                            TextButton(onClick = { data.dismiss() }) {
                                Text(label)
                            }
                        }
                    },
                ) {
                    Text(data.visuals.message)
                }
            }
        }
    }
}

@Composable
private fun PortfolioAccountBlock(
    accountKey: AccountKey,
    accountPortfolio: AccountPortfolio,
    prices: Map<String, Double>,
    priceChangePercents: Map<String, Double>,
    tradingStatuses: Map<String, TradingAvailability>,
    sandboxMode: Boolean,
    onPayIn: () -> Unit,
) {
    AccountHeaderCard(
        accountKey = accountKey,
        accountPortfolio = accountPortfolio,
        sandboxMode = sandboxMode,
        onPayIn = onPayIn,
    )
    val positions = accountPortfolio.positions
    if (positions.isEmpty()) {
        Text(
            "Нет позиций",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        positions.forEach { position ->
            val id = position.instrument.tscalpInstrumentId
            AssetPositionCard(
                position = position,
                displayCurrentPrice = prices[id],
                priceChangePercent = priceChangePercents[id],
                tradingAvailability =
                    tradingStatuses[id]
                        ?: TradingAvailability.UNKNOWN,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun AccountHeaderCard(
    accountKey: AccountKey,
    accountPortfolio: AccountPortfolio?,
    sandboxMode: Boolean,
    onPayIn: () -> Unit,
) {
    val rubBalance =
        accountPortfolio
            ?.balances
            ?.firstOrNull { it.currency.equals("rub", ignoreCase = true) }
            ?.total
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    accountKey.brokerName.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    maskAccountId(accountKey.accountId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    rubBalance?.let { formatDomesticCurrency(it) } ?: "нет данных",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (sandboxMode && accountKey.brokerName == BrokerName.TINVEST) {
                Button(onClick = onPayIn) {
                    Text("Пополнить")
                }
            }
        }
    }
}

private const val MASK_VISIBLE_DIGITS = 4
private const val MASK_BULLET = "\u2022"

private fun maskAccountId(id: String): String =
    if (id.length <= MASK_VISIBLE_DIGITS) {
        id
    } else {
        MASK_BULLET.repeat(MASK_VISIBLE_DIGITS) + id.takeLast(MASK_VISIBLE_DIGITS)
    }

@Composable
fun ApiNotInitializedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "⚠️ API не подключен",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Перейдите в Настройки и введите токен доступа",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun EmptyPortfolioCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "📊 Портфель пуст",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "У вас нет активных позиций",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
