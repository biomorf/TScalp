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
 * - [OrdersScrollableState.toLegacyFormState] — синтез legacy `OrderFormState`
 *   для 3 дочерних (`OrdersInstrumentSection` / `OrdersConfirmDialogSection` /
 *   `OrdersPairSection`), которые переедут на sub-state в Phase 5d/5e.
 *
 * **Clean-альтернатива:** дочерние принимают 4 sub-state напрямую (Phase
 * 5d/5e); фабрика `OrdersScrollableState` — из 4 sub-state без legacy
 * (Phase 6, после удаления Coord).
 *
 * **Выгоды:** Phase 5b/5c изолированы — `OrdersScrollableState` сразу
 * в новой форме, `OrdersScrollableContent` читает sub-state напрямую;
 * `OrdersScreen` / baseline не раздуваются на 4 sub-state в конструкторе.
 * Adapter'ы умирают в Phase 5d/5e (после перевода дочерних) и Phase 6
 * (после удаления Coord) — 2 задачи.
 *
 * **Ссылка на задачу:** PR-4b, Phase 5b/5c (см. `docs/ROADMAP.md` →
 * `OrderFormViewModel: split` → «PR-4b»).
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
        instrumentState =
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
            ),
        inputState =
            OrderInputState(
                quantity = quantity,
                limitPrice = limitPrice,
                stopPrice = stopPrice,
                orderType = orderType,
                expirationType = expirationType,
                expireDate = expireDate,
                pairTradingEnabled = pairTradingEnabled,
                pairedMultiplier = pairedMultiplier,
            ),
        tradeDetailsState =
            OrderTradeDetailsState(
                executionPrice = executionPrice,
                costOverlay = costOverlay,
                multiplierOverlay = multiplierOverlay,
                pairExecPrice = pairExecPrice,
                pairedOrderType = pairedOrderType,
            ),
        executionState =
            OrderExecutionState(
                showConfirmDialog = showConfirmDialog,
                pendingDirection = pendingDirection,
                confirmOrdersEnabled = confirmOrdersEnabled,
                isLoading = isLoading,
                freeBalance = freeBalance,
            ),
        tradingStatuses = tradingStatuses,
        resetSwipe = resetSwipe,
    )

/**
 * 2d-adapter: синтез legacy `OrderFormState` из 4 sub-state для 3 дочерних
 * (`OrdersInstrumentSection` / `OrdersConfirmDialogSection` / `OrdersPairSection`).
 * Удаляется в Phase 5d/5e (после перевода дочерних на sub-state).
 */
internal fun OrdersScrollableState.toLegacyFormState(): OrderFormState =
    OrderFormState(
        ticker = instrumentState.ticker,
        selectedInstrument = instrumentState.selectedInstrument,
        currentPrice = instrumentState.currentPrice,
        pairCurrentPrice = instrumentState.pairCurrentPrice,
        isPriceLoading = instrumentState.isPriceLoading,
        selectedPriceChangePercent = instrumentState.selectedPriceChangePercent,
        currentPointValue = instrumentState.currentPointValue,
        pairedPointValue = instrumentState.pairedPointValue,
        quantity = inputState.quantity,
        limitPrice = inputState.limitPrice,
        stopPrice = inputState.stopPrice,
        orderType = inputState.orderType,
        expirationType = inputState.expirationType,
        expireDate = inputState.expireDate,
        selectedBroker = instrumentState.selectedBroker,
        selectedAccountId = instrumentState.selectedAccountId,
        pairedBroker = instrumentState.pairedBroker,
        pairedAccountId = instrumentState.pairedAccountId,
        pairTradingEnabled = inputState.pairTradingEnabled,
        pairedInstrument = instrumentState.pairedInstrument,
        pairedMultiplier = inputState.pairedMultiplier,
        pairExecPrice = tradeDetailsState.pairExecPrice,
        pairedOrderType = tradeDetailsState.pairedOrderType,
        executionPrice = tradeDetailsState.executionPrice,
        costOverlay = tradeDetailsState.costOverlay,
        multiplierOverlay = tradeDetailsState.multiplierOverlay,
        freeBalance = executionState.freeBalance,
        showConfirmDialog = executionState.showConfirmDialog,
        pendingDirection = executionState.pendingDirection,
        confirmOrdersEnabled = executionState.confirmOrdersEnabled,
        isLoading = executionState.isLoading,
    )
