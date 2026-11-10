package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.presentation.components.AssetPositionCard
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import com.gitlab.biomorf.tscalp.presentation.util.withCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val PRICE_FIELDS_ANIMATION_MS = 200

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
internal fun OrdersPairSection(
    coordState: OrdersCoordState,
    searchState: OrderSearchState,
    formState: OrderFormState,
    tradingStatuses: Map<String, TradingAvailability>,
    resetSwipe: Boolean,
    scrollState: ScrollState,
    scope: CoroutineScope,
    onPairSearchQueryChanged: (String) -> Unit,
    onPairedInstrumentSelected: (Instrument) -> Unit,
    onClearPairSearch: () -> Unit,
    onRefreshPairSearch: () -> Unit,
    onOpenPairSearchBrokerSettings: () -> Unit,
    onPairTradingEnabledChanged: (Boolean) -> Unit,
    onClearPairedInstrument: () -> Unit,
    onOpenBrokerDialog: (Instrument, String?) -> Unit,
    onPairedMultiplierChanged: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Парная торговля", style = MaterialTheme.typography.titleSmall)
        Switch(
            checked = formState.pairTradingEnabled,
            onCheckedChange = onPairTradingEnabledChanged,
        )
    }
    if (!formState.pairTradingEnabled) return
    if (formState.pairedInstrument == null) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InstrumentSearchField(
                    query = searchState.pairSearchQuery,
                    onQueryChanged = onPairSearchQueryChanged,
                    isSearching = searchState.isPairSearching,
                    searchResults = searchState.pairSearchResults,
                    onInstrumentSelected = onPairedInstrumentSelected,
                    onClear = onClearPairSearch,
                    recentInstruments = coordState.lastSelectedInstruments.map { it.instrument },
                    tradingStatuses = tradingStatuses,
                    modifier = Modifier.weight(1f),
                )
                if (searchState.pairSearchQuery.length >= 2 && !searchState.isPairSearching) {
                    IconButton(onClick = onRefreshPairSearch) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить поиск")
                    }
                }
                IconButton(onClick = onOpenPairSearchBrokerSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки поиска")
                }
            }
        }
    } else {
        formState.pairedInstrument.let { instrument: Instrument ->
            val card = coordState.activePairedCardFor(formState.pairedInstrument, formState.pairedAccountId)
            val pairPrice =
                formState.pairCurrentPrice
                    ?: card?.currentPrice
                    ?: 0.0
            val position =
                emptyPosition(
                    instrument = instrument,
                    currentPrice = pairPrice,
                    accountId = card?.accountId ?: coordState.selectedAccountId ?: "",
                    brokerName = card?.brokerName ?: BrokerName.TINVEST,
                ).withCard(card)
            AssetPositionCard(
                position = position,
                displayCurrentPrice = pairPrice,
                priceChangePercent = formState.selectedPriceChangePercent,
                onDelete = onClearPairedInstrument,
                onSettings = { onOpenBrokerDialog(instrument, formState.pairedAccountId) },
                onClick = { },
                isSelected = false,
                resetSwipe = resetSwipe,
                tradingAvailability = tradingStatuses[instrument.tscalpInstrumentId],
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formState.pairedMultiplier,
                    onValueChange = onPairedMultiplierChanged,
                    label = { Text("Множитель") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            },
                )
                val multiplierOverlay = formState.multiplierOverlay
                if (multiplierOverlay != null) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .padding(end = 12.dp, top = 8.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            text = multiplierOverlay,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        RoundedCornerShape(4.dp),
                                    ).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// Архитектурный долг: 63 строки > 60 (4 ветки AnimatedContent).
// См. ROADMAP → «Compose-экраны» (Н94).
@Suppress("LongMethod")
@Composable
internal fun OrdersPriceFields(
    orderType: OrderTypeSelection,
    limitPrice: String,
    stopPrice: String,
    onLimitPriceChanged: (String) -> Unit,
    onStopPriceChanged: (String) -> Unit,
) {
    AnimatedContent(
        targetState = orderType,
        transitionSpec = {
            fadeIn(animationSpec = tween(PRICE_FIELDS_ANIMATION_MS)) togetherWith
                fadeOut(animationSpec = tween(PRICE_FIELDS_ANIMATION_MS))
        },
        label = "price_fields",
    ) { type: OrderTypeSelection ->
        when (type) {
            OrderTypeSelection.Limit -> {
                OutlinedTextField(
                    value = limitPrice,
                    onValueChange = onLimitPriceChanged,
                    label = { Text("Цена за лот") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OrderTypeSelection.StopLoss,
            OrderTypeSelection.TakeProfit,
            -> {
                Column {
                    OutlinedTextField(
                        value = stopPrice,
                        onValueChange = onStopPriceChanged,
                        label = { Text("Триггер стоп-цена") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            OrderTypeSelection.StopLimit -> {
                Column {
                    OutlinedTextField(
                        value = stopPrice,
                        onValueChange = onStopPriceChanged,
                        label = { Text("Триггер стоп-цена") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = limitPrice,
                        onValueChange = onLimitPriceChanged,
                        label = { Text("Лимитная цена") },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> { /* Market – ничего не показываем */ }
        }
    }
}

@Composable
internal fun OrdersTypeChips(
    orderType: OrderTypeSelection,
    onOrderTypeChanged: (OrderTypeSelection) -> Unit,
) {
    Column(
        modifier = Modifier.wrapContentWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = orderType == OrderTypeSelection.Market,
                onClick = { onOrderTypeChanged(OrderTypeSelection.Market) },
                label = { Text("Рын.") },
            )
            FilterChip(
                selected = orderType == OrderTypeSelection.Limit,
                onClick = { onOrderTypeChanged(OrderTypeSelection.Limit) },
                label = { Text("Лим.") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = orderType == OrderTypeSelection.StopLoss,
                onClick = { onOrderTypeChanged(OrderTypeSelection.StopLoss) },
                label = { Text("Stop‑Loss") },
            )
            FilterChip(
                selected = orderType == OrderTypeSelection.TakeProfit,
                onClick = { onOrderTypeChanged(OrderTypeSelection.TakeProfit) },
                label = { Text("Take‑Profit") },
            )
            FilterChip(
                selected = orderType == OrderTypeSelection.StopLimit,
                onClick = { onOrderTypeChanged(OrderTypeSelection.StopLimit) },
                label = { Text("Stop‑Limit") },
            )
        }
    }
}

// Архитектурный долг: 62 строки > 60 (input + cost overlay + / +).
// См. ROADMAP → «Compose-экраны» (Н94).
@Suppress("LongMethod")
@Composable
internal fun OrdersQuantityRow(
    quantity: String,
    costOverlay: String?,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onQuantityChanged: (String) -> Unit,
) {
    val quantityAsLong = quantity.toLongOrNull() ?: 0L
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            onClick = {
                if (quantityAsLong > 0) onQuantityChanged((quantityAsLong - 1).toString())
            },
            enabled = canDecrement,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.Remove, "Уменьшить", modifier = Modifier.size(24.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = quantity,
                onValueChange = onQuantityChanged,
                label = { Text("Кол-во лотов") },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (costOverlay != null) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .padding(end = 12.dp, top = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = costOverlay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier =
                            Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    RoundedCornerShape(4.dp),
                                ).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        IconButton(
            onClick = {
                onQuantityChanged((quantityAsLong + 1).toString())
            },
            enabled = canIncrement,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.Default.Add, "Увеличить", modifier = Modifier.size(24.dp))
        }
    }
}


@Preview(name = "QuantityRow — enabled", showBackground = true, widthDp = 300)
@Composable
private fun OrdersQuantityRowEnabledPreview() {
    TScalpTheme {
        OrdersQuantityRow(
            quantity = "10",
            costOverlay = "1 234,50 ₽",
            canDecrement = true,
            canIncrement = true,
            onQuantityChanged = {},
        )
    }
}


@Preview(name = "QuantityRow — disabled", showBackground = true, widthDp = 300)
@Composable
private fun OrdersQuantityRowDisabledPreview() {
    TScalpTheme {
        OrdersQuantityRow(
            quantity = "0",
            costOverlay = null,
            canDecrement = false,
            canIncrement = true,
            onQuantityChanged = {},
        )
    }
}


@Preview(name = "TypeChips — Market", showBackground = true, widthDp = 300)
@Composable
private fun OrdersTypeChipsMarketPreview() {
    TScalpTheme {
        OrdersTypeChips(
            orderType = OrderTypeSelection.Market,
            onOrderTypeChanged = {},
        )
    }
}


@Preview(name = "TypeChips — Limit", showBackground = true, widthDp = 300)
@Composable
private fun OrdersTypeChipsLimitPreview() {
    TScalpTheme {
        OrdersTypeChips(
            orderType = OrderTypeSelection.Limit,
            onOrderTypeChanged = {},
        )
    }
}
