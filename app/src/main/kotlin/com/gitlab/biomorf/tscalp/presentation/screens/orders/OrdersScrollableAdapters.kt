package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability

/**
 * Adapter'ы Phase 5b (PR-4b, C2): связка legacy `OrderFormState`
 * ↔ 4 sub-state Form-VM.
 *
 * **2d (временное решение, 2026-11-14):**
 * - [OrderFormState.toOrdersScrollableState] — фабрика `OrdersScrollableState`
 *   из legacy `OrderFormState`. Используется `OrdersScreen` и baseline-тест
 *   до Phase 6 (Coord `OrderFormViewModel` умрёт).
 *
 * `OrdersScrollableState.toLegacyFormState` (синтез legacy `OrderFormState`
 * для 3 дочерних) **удалён в Phase 5d** — все 3 дочерних (`OrdersInstrumentSection`
 * / `OrdersConfirmDialogSection` / `OrdersPairSection`) переведены на
 * sub-state параметры.
 *
 * **Clean-альтернатива:** фабрика `OrdersScrollableState` — из 4 sub-state
 * без legacy (Phase 6, после удаления Coord).
 *
 * **Выгоды:** `OrdersScrollableState` в новой форме (Phase 5b),
 * `OrdersScrollableContent` и 3 дочерних читают sub-state напрямую
 * (Phase 5c/5d); `OrdersScreen` / baseline не раздуваются на 4 sub-state
 * в конструкторе. Adapter умирает в Phase 6.
 *
 * **Ссылка на задачу:** PR-4b, Phase 5b/5c (см. `docs/ROADMAP.md` →
 * `OrderFormViewModel: split` → «PR-4b»).
 */
internal fun OrderFormState.toInstrumentState(): OrderInstrumentState =
    OrderInstrumentState(
        ticker = ticker,
        selectedInstrument = selectedInstrument,
        currentPrice = currentPrice,
        selectedPriceChangePercent = selectedPriceChangePercent,
        currentPointValue = currentPointValue,
        isPriceLoading = isPriceLoading,
        selectedBroker = selectedBroker,
        selectedAccountId = selectedAccountId,
        pairedInstrument = pairedInstrument,
        pairCurrentPrice = pairCurrentPrice,
        pairedPointValue = pairedPointValue,
        pairedBroker = pairedBroker,
        pairedAccountId = pairedAccountId,
    )

internal fun OrderFormState.toInputState(): OrderInputState =
    OrderInputState(
        quantity = quantity,
        limitPrice = limitPrice,
        stopPrice = stopPrice,
        orderType = orderType,
        expirationType = expirationType,
        expireDate = expireDate,
        pairTradingEnabled = pairTradingEnabled,
        pairedMultiplier = pairedMultiplier,
    )

internal fun OrderFormState.toTradeDetailsState(): OrderTradeDetailsState =
    OrderTradeDetailsState(
        executionPrice = executionPrice,
        costOverlay = costOverlay,
        multiplierOverlay = multiplierOverlay,
        pairExecPrice = pairExecPrice,
        pairedOrderType = pairedOrderType,
    )

internal fun OrderFormState.toExecutionState(): OrderExecutionState =
    OrderExecutionState(
        showConfirmDialog = showConfirmDialog,
        pendingDirection = pendingDirection,
        confirmOrdersEnabled = confirmOrdersEnabled,
        isLoading = isLoading,
        freeBalance = freeBalance,
    )

/**
 * 2d-helper (Phase 5f, PR-4b): 4 sub-state из legacy `OrderFormState`.
 * Используется фабрикой [toOrdersScrollableState] и baseline-тестом
 * (`OrdersScreensScreenshotTest`). Удаляется в Phase 6 вместе с фабрикой.
 */
internal fun OrderFormState.toOrdersScrollableState(
    coordState: OrdersCoordState,
    searchState: OrderSearchState,
    tradingStatuses: Map<String, TradingAvailability>,
    resetSwipe: Boolean,
): OrdersScrollableState =
    OrdersScrollableState(
        coordState = coordState,
        searchState = searchState,
        instrumentState = toInstrumentState(),
        inputState = toInputState(),
        tradeDetailsState = toTradeDetailsState(),
        executionState = toExecutionState(),
        tradingStatuses = tradingStatuses,
        resetSwipe = resetSwipe,
    )
