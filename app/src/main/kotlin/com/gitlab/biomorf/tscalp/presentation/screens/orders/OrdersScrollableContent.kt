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
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import kotlinx.coroutines.CoroutineScope

/**
 * State для [OrdersScrollableContent]: 5 state-полей из 3 VM.
 *
 * **2c-фиксация (Pass-2a):** state и callbacks в 2 data class, а не
 * в 22+ параметров `@Composable`-функции. Причина: 27 параметров
 * функции → detekt `LongParameterList` (>6). `data class` — не функция
 * (детект exempt), проверено на существующих `OrderFormState` /
 * `OrderSearchState` / `OrdersCoordState`. Плюс: behavioral-тест
 * (Pass-2b) может вызвать `OrdersScrollableContent(state = stub,
 * callbacks = no-op)` без Hilt.
 */
internal data class OrdersScrollableState(
    val coordState: OrdersCoordState,
    val searchState: OrderSearchState,
    val formState: OrderFormState,
    val tradingStatuses: Map<String, TradingAvailability>,
    val resetSwipe: Boolean,
)

internal data class OrdersScrollableCallbacks(
    val onSearchQueryChanged: (String) -> Unit,
    val onInstrumentSelected: (Instrument) -> Unit,
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
    val onPairedInstrumentSelected: (Instrument) -> Unit,
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
                formState = state.formState,
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
                formState = state.formState,
                onDismiss = callbacks.onDismissConfirmDialog,
                onConfirm = callbacks.onConfirmOrder,
            )

            OrdersQuantityRow(
                quantity = state.formState.quantity,
                quantityAsLong = state.formState.quantityAsLong ?: 0L,
                costOverlay = state.formState.costOverlay,
                canDecrement = (state.formState.quantityAsLong ?: 0L) > 0 && state.formState.selectedInstrument != null,
                canIncrement = state.formState.selectedInstrument != null,
                onQuantityChanged = callbacks.onQuantityChanged,
            )

            OrdersTypeChips(
                orderType = state.formState.orderType,
                onOrderTypeChanged = callbacks.onOrderTypeChanged,
            )

            OrdersPriceFields(
                orderType = state.formState.orderType,
                limitPrice = state.formState.limitPrice,
                stopPrice = state.formState.stopPrice,
                onLimitPriceChanged = callbacks.onLimitPriceChanged,
                onStopPriceChanged = callbacks.onStopPriceChanged,
            )

            OrdersPairSection(
                coordState = state.coordState,
                searchState = state.searchState,
                formState = state.formState,
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
