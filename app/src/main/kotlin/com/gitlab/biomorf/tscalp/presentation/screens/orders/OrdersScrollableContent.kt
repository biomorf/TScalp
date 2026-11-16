package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import kotlinx.coroutines.CoroutineScope

/**
 * State для [OrdersScrollableContent]: 8 state-полей (coord / search /
 * 4 sub-state Form / tradingStatuses / resetSwipe) из 5 VM.
 *
 * **2c-фиксация (Pass-2a):** state и callbacks в 2 data class, а не
 * в 30+ параметров `@Composable`-функции. Причина: 27 параметров
 * функции → detekt `LongParameterList` (>6). `data class` — не функция
 * (детект exempt), проверено на существующих `OrderFormState` /
 * `OrderSearchState` / `OrdersCoordState`. Плюс: behavioral-тест
 * (Pass-2b) может вызвать `OrdersScrollableContent(state = stub,
 * callbacks = no-op)` без Hilt.
 *
 * **Phase 5b (C2, PR-4b, 2026-11-14):** `formState: OrderFormState`
 * заменён на 4 sub-state из Form-VM (`OrderInstrumentState` /
 * `OrderInputState` / `OrderTradeDetailsState` / `OrderExecutionState`);
 * legacy-форма собирается adapter'ом `toLegacyFormState()` только для
 * 3 дочерних (`OrdersInstrumentSection` / `OrdersConfirmDialogSection` /
 * `OrdersPairSection`), которые переедут на sub-state в Phase 5d/5e.
 * Adapter `toLegacyFormState` удалён в Phase 5d (все 3 дочерних на sub-state).
 */
internal data class OrdersScrollableState(
    val coordState: OrdersCoordState,
    val searchState: OrderSearchState,
    val instrumentState: OrderInstrumentState,
    val inputState: OrderInputState,
    val tradeDetailsState: OrderTradeDetailsState,
    val executionState: OrderExecutionState,
    val tradingStatuses: Map<String, TradingAvailability>,
    val resetSwipe: Boolean,
)

internal data class OrdersScrollableCallbacks(
    val onSearchQueryChanged: (String) -> Unit,
    val onInstrumentSelected: (tscalpInstrumentId: String, ticker: String, name: String, brokerName: BrokerName) -> Unit,
    val onClearSearch: () -> Unit,
    val onRefreshSearch: () -> Unit,
    val onOpenSearchSettings: () -> Unit,
    val onClearSelected: () -> Unit,
    val onOpenBrokerDialog: (Instrument, String?, CardSlot) -> Unit,
    val onDismissConfirmDialog: () -> Unit,
    val onConfirmOrder: () -> Unit,
    val onQuantityChanged: (String) -> Unit,
    val onOrderTypeChanged: (OrderTypeSelection) -> Unit,
    val onLimitPriceChanged: (String) -> Unit,
    val onStopPriceChanged: (String) -> Unit,
    val onPairSearchQueryChanged: (String) -> Unit,
    val onPairedInstrumentSelected: (tscalpInstrumentId: String, ticker: String, name: String, brokerName: BrokerName) -> Unit,
    val onClearPairSearch: () -> Unit,
    val onRefreshPairSearch: () -> Unit,
    val onOpenPairSearchBrokerSettings: () -> Unit,
    val onPairTradingEnabledChanged: (Boolean) -> Unit,
    val onClearPairedInstrument: () -> Unit,
    val onPairedMultiplierChanged: (String) -> Unit,
)

/**
 * Скроллируемое содержимое экрана «Заявки»: форма + количество + тип +
 * цена + парная секция + индикаторы прокрутки.
 *
 * **Pass-2a (state hoisting локального UI-state):** `rememberScrollState`
 * / `rememberCoroutineScope` / `showTopShadow` / `showBottomShadow` —
 * **внутри** child, наружу не hoist-ятся. `ColumnScope` — для
 * `Modifier.weight(1f)`.
 *
 * **2c-fixation (Pass-2a):** 82 строки > 60 (detekt `LongMethod`). Разбиение на `OrdersScrollableForm` + `OrdersScrollablePair` даёт 2 функции по ~50 строк, но forward 22 callbacks (каждый параметр — линия). Альтернатива — передавать `*ViewModel` (как `OrdersDialogsSection`), но тогда behavioral-тест снова требует Hilt. Выгоды текущего варианта: 1 `@Composable` с 2 параметрами (state, callbacks), поведение 1:1, тесты без Hilt. Минус: `@Suppress("LongMethod")` — явный маркер (как у `OrdersScreen` 123 и `OrdersPairSection` 127).
 *
 * **Pass-3 skip:** `@Preview` не добавлен — 22 callbacks + 5 state-полей ≈ 40 строк stub-кода;
 * anti-pattern в main-source. Добавлены на чистые извлечённые composables
 * (`OrdersQuantityRow` / `OrdersTypeChips` / `SearchResultItem`). Если понадобится — вынести
 * stub в отдельный `preview/`-файл.
 */
@Suppress("LongMethod")
@Composable
internal fun ColumnScope.OrdersScrollableContent(
    state: OrdersScrollableState,
    callbacks: OrdersScrollableCallbacks,
) {
    val scrollState = rememberScrollState()
    val scope: CoroutineScope = rememberCoroutineScope()
    val showTopShadow by remember { derivedStateOf { scrollState.value > 0 } }
    val showBottomShadow by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue }
    }

    Box(modifier = Modifier.weight(1f)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OrdersInstrumentSection(
                coordState = state.coordState,
                searchState = state.searchState,
                instrumentState = state.instrumentState,
                tradingStatuses = state.tradingStatuses,
                resetSwipe = state.resetSwipe,
                onSearchQueryChanged = callbacks.onSearchQueryChanged,
                onInstrumentSelected = callbacks.onInstrumentSelected,
                onClearSearch = callbacks.onClearSearch,
                onRefreshSearch = callbacks.onRefreshSearch,
                onOpenSearchSettings = callbacks.onOpenSearchSettings,
                onClearSelected = callbacks.onClearSelected,
                onOpenBrokerDialog = { inst, acc ->
                    callbacks.onOpenBrokerDialog(inst, acc, CardSlot.PRIMARY)
                },
            )

            OrdersConfirmDialogSection(
                instrumentState = state.instrumentState,
                inputState = state.inputState,
                tradeDetailsState = state.tradeDetailsState,
                executionState = state.executionState,
                onDismiss = callbacks.onDismissConfirmDialog,
                onConfirm = callbacks.onConfirmOrder,
            )

            OrdersQuantityRow(
                quantity = state.inputState.quantity,
                quantityAsLong = state.inputState.quantityAsLong ?: 0L,
                costOverlay = state.tradeDetailsState.costOverlay,
                canDecrement = (state.inputState.quantityAsLong ?: 0L) > 0 && state.instrumentState.selectedInstrument != null,
                canIncrement = state.instrumentState.selectedInstrument != null,
                onQuantityChanged = callbacks.onQuantityChanged,
            )

            OrdersTypeChips(
                orderType = state.inputState.orderType,
                onOrderTypeChanged = callbacks.onOrderTypeChanged,
            )

            OrdersPriceFields(
                orderType = state.inputState.orderType,
                limitPrice = state.inputState.limitPrice,
                stopPrice = state.inputState.stopPrice,
                onLimitPriceChanged = callbacks.onLimitPriceChanged,
                onStopPriceChanged = callbacks.onStopPriceChanged,
            )

            OrdersPairSection(
                coordState = state.coordState,
                searchState = state.searchState,
                instrumentState = state.instrumentState,
                inputState = state.inputState,
                tradeDetailsState = state.tradeDetailsState,
                tradingStatuses = state.tradingStatuses,
                resetSwipe = state.resetSwipe,
                scrollState = scrollState,
                scope = scope,
                onPairSearchQueryChanged = callbacks.onPairSearchQueryChanged,
                onPairedInstrumentSelected = callbacks.onPairedInstrumentSelected,
                onClearPairSearch = callbacks.onClearPairSearch,
                onRefreshPairSearch = callbacks.onRefreshPairSearch,
                onOpenPairSearchBrokerSettings = callbacks.onOpenPairSearchBrokerSettings,
                onPairTradingEnabledChanged = callbacks.onPairTradingEnabledChanged,
                onClearPairedInstrument = callbacks.onClearPairedInstrument,
                onOpenBrokerDialog = { inst, acc ->
                    callbacks.onOpenBrokerDialog(inst, acc, CardSlot.PAIR)
                },
                onPairedMultiplierChanged = callbacks.onPairedMultiplierChanged,
            )
        }

        OrdersScreenShadows(
            showTopShadow = showTopShadow,
            showBottomShadow = showBottomShadow,
        )
    }
}
