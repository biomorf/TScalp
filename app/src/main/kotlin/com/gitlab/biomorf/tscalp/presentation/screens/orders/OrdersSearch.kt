package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.presentation.components.AssetPositionCard
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import com.gitlab.biomorf.tscalp.presentation.theme.getInstrumentTypeColor
import com.gitlab.biomorf.tscalp.presentation.util.withCard

@Composable
internal fun OrdersInstrumentSection(
    coordState: OrdersCoordState,
    searchState: OrderSearchState,
    formState: OrderFormState,
    tradingStatuses: Map<String, TradingAvailability>,
    resetSwipe: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onInstrumentSelected: (Instrument) -> Unit,
    onClearSearch: () -> Unit,
    onRefreshSearch: () -> Unit,
    onOpenSearchSettings: () -> Unit,
    onClearSelected: () -> Unit,
    onOpenBrokerDialog: (Instrument, String?) -> Unit,
) {
    if (formState.selectedInstrument == null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentSearchField(
                    query = searchState.primary.query,
                    onQueryChanged = onSearchQueryChanged,
                    isSearching = searchState.primary.isSearching,
                    searchResults = searchState.primary.results,
                    onInstrumentSelected = onInstrumentSelected,
                    onClear = onClearSearch,
                    recentInstruments = coordState.lastSelectedInstruments.map { it.instrument },
                    tradingStatuses = tradingStatuses,
                    modifier = Modifier.weight(1f),
                )
                if (searchState.primary.query.length >= 2 && !searchState.primary.isSearching) {
                    IconButton(onClick = onRefreshSearch) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить поиск")
                    }
                }
                IconButton(onClick = onOpenSearchSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки поиска")
                }
            }
        }
    } else {
        formState.selectedInstrument.let { instrument: Instrument ->
            val card = coordState.activeCardFor(formState.selectedInstrument, formState.selectedAccountId)
            val currentPrice = formState.currentPrice ?: card?.currentPrice ?: 0.0
            val position =
                emptyPosition(
                    instrument = instrument,
                    currentPrice = currentPrice,
                    accountId = card?.accountId ?: coordState.selectedAccountId ?: "",
                    brokerName = card?.brokerName ?: BrokerName.TINVEST,
                ).withCard(card)
            AssetPositionCard(
                position = position,
                displayCurrentPrice = currentPrice,
                priceChangePercent = formState.selectedPriceChangePercent,
                onDelete = onClearSelected,
                onSettings = { onOpenBrokerDialog(instrument, formState.selectedAccountId) },
                onClick = { },
                isSelected = false,
                resetSwipe = resetSwipe,
                tradingAvailability = tradingStatuses[instrument.tscalpInstrumentId],
            )
        }
    }
}

@Composable
internal fun SearchResultItem(
    instrument: Instrument,
    onClick: () -> Unit,
) {
    val typeColor = getInstrumentTypeColor(instrument.instrumentType)
    ListItem(
        headlineContent = { Text("${instrument.ticker} - ${instrument.name}") },
        supportingContent = {
            Text(
                text = instrument.tscalpInstrumentId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(typeColor),
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstrumentSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<Instrument>,
    onInstrumentSelected: (Instrument) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    recentInstruments: List<Instrument> = emptyList(),
    tradingStatuses: Map<String, TradingAvailability> = emptyMap(),
) {
    var inputText by remember { mutableStateOf(query) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isEmpty() && inputText.isNotEmpty()) {
            inputText = ""
            expanded = false
        }
    }

    Column(modifier = modifier) {
        DockedSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = inputText,
                    onQueryChange = { newText: String ->
                        inputText = newText
                        onQueryChanged(newText)
                        expanded = newText.isNotEmpty() || recentInstruments.isNotEmpty()
                    },
                    onSearch = { expanded = false },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = {
                        Text(
                            "Введите тикер или название",
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                ),
                        )
                    },
                    leadingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(onClick = {
                                inputText = ""
                                onClear()
                                expanded = false
                            }) {
                                Icon(Icons.Default.Clear, "Очистить")
                            }
                        } else {
                            Icon(Icons.Default.Search, "Поиск")
                        }
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        }
                    },
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (expanded && (searchResults.isNotEmpty() || (inputText.isEmpty() && recentInstruments.isNotEmpty()))) {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    if (inputText.isEmpty() && recentInstruments.isNotEmpty()) {
                        recentInstruments.forEach { instrument ->
                            val status = tradingStatuses[instrument.tscalpInstrumentId]
                            if (status == TradingAvailability.UNAVAILABLE) return@forEach
                            SearchResultItem(
                                instrument = instrument,
                                onClick = {
                                    onInstrumentSelected(instrument)
                                    inputText = "${instrument.ticker} - ${instrument.name}"
                                    expanded = false
                                },
                            )
                        }
                    } else {
                        searchResults.forEach { instrument ->
                            val status = tradingStatuses[instrument.tscalpInstrumentId]
                            if (status == TradingAvailability.UNAVAILABLE) return@forEach
                            SearchResultItem(
                                instrument = instrument,
                                onClick = {
                                    onInstrumentSelected(instrument)
                                    inputText = "${instrument.ticker} - ${instrument.name}"
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "SearchResultItem — Share", showBackground = true, widthDp = 400)
@Composable
private fun SearchResultItemSharePreview() {
    TScalpTheme {
        SearchResultItem(
            instrument =
                Share(
                    brokerName = BrokerName.TINVEST,
                    tscalpInstrumentId = "uid-preview",
                    ticker = "SBER",
                    name = "Сбербанк",
                    isin = "BBG004730N88",
                    classCode = "TQBR",
                    currency = "rub",
                ),
            onClick = {},
        )
    }
}
