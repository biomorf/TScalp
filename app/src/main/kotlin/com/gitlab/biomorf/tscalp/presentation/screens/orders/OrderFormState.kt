package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType

/**
 * Состояние формы заявки (Form-VM, шаг 13d-A).
 *
 * Вынесено из `OrdersUiState` (split `OrdersViewModel` на 4 VM,
 * M013 / A2). Семантика полей 1:1, кроме 2c-фиксаций ниже.
 *
 * **2c (фиксация, 2026-11-07):**
 * - `isFormValid` — pure-функция с параметром `isApiInitialized`
 *   (Coord). **Правило Clean:** cross-VM (`isFormValid` зависит от
 *   Form-полей + Coord-`isApiInitialized`). **Clean-альтернатива:**
 *   параметр `isApiInitialized`; UI: `formState.isFormValid(
 *   coordState.isApiInitialized)`. **Выгоды:** Coord — единственный
 *   владелец инициализации (`loadAccounts` / `checkApiInitialization`);
 *   Form не дублирует.
 * - `activeCard` / `activePairedCard` — **не в State** (cross-VM:
 *   Form-`selectedInstrument` + Coord-`lastSelectedInstruments`).
 *   Становятся pure extension на Coord-State (13d-F):
 *   `fun OrdersCoordState.activeCardFor(inst: Instrument?)`.
 *   UI-composable вызывает. **Правило Clean:** cross-VM.
 *   **Clean-альтернатива:** extension на Coord-State.
 *   **Выгоды:** жёсткая централизация; Coord — владелец
 *   `lastSelectedInstruments`; Form — `selectedInstrument`.
 * - `saveState()` — в Form-VM (не в Coord): все callsites —
 *   Form-методы; Coord-State после 13d-F не содержит Form-полей.
 * - `isLoading` — **Form-level** (postOrder / confirm dialog);
 *   статусные события — `OrderFormViewModel.status: Flow<StatusEvent>`
 *   (M6, clean-audit M6).
 */
data class OrderFormState(
    // Instrument / price
    val ticker: String = "",
    val selectedInstrument: Instrument? = null,
    val currentPrice: Double? = null,
    val pairCurrentPrice: Double? = null,
    val isPriceLoading: Boolean = false,
    val selectedPriceChangePercent: Double? = null,
    val currentPointValue: Double? = null,
    val pairedPointValue: Double? = null,
    // Quantity / price / order type
    val quantity: String = "",
    val limitPrice: String = "",
    val stopPrice: String = "",
    val orderType: OrderTypeSelection = OrderTypeSelection.Market,
    val expirationType: StopOrderExpirationType = StopOrderExpirationType.GOOD_TILL_CANCEL,
    val expireDate: String? = null,
    // Slot (broker + account; Фаза 1b)
    // selectedBroker / pairedBroker — из instrument.brokerName при выборе;
    // selectedAccountId / pairedAccountId — из SelectInstrumentResult.card.accountId
    // (paired: null, источник в SelectPairedResult отсутствует) или через
    // onAccountSaved / onPairedAccountSaved callback (UI-оркестрация после save).
    val selectedBroker: BrokerName = BrokerName.TINVEST,
    val selectedAccountId: String? = null,
    val pairedBroker: BrokerName = BrokerName.TINVEST,
    val pairedAccountId: String? = null,
    // Pair
    val pairTradingEnabled: Boolean = false,
    val pairedInstrument: Instrument? = null,
    val pairedMultiplier: String = "1",
    val pairExecPrice: Double = 0.0,
    val pairedOrderType: OrderTypeSelection? = null,
    // Trade details
    val executionPrice: Double = 0.0,
    val costOverlay: String? = null,
    val multiplierOverlay: String? = null,
    val freeBalance: Double? = null,
    // Confirm dialog (M009)
    val showConfirmDialog: Boolean = false,
    val pendingDirection: OrderDirection? = null,
    /** M009: подтверждение заявок (Form владелец confirm-диалога, M009). */
    val confirmOrdersEnabled: Boolean = true,
    // Status (Form-level; M6: статусные события — FormViewModel.status)
    val isLoading: Boolean = false,
) {
    /**
     * Валидность формы: 1:1 с `OrdersUiState.isFormValid`, кроме
     * `isApiInitialized` — параметр (2c, см. KDoc класса).
     */
    fun isFormValid(isApiInitialized: Boolean): Boolean =
        isBasicValid(isApiInitialized) &&
            isLimitPriceValid() &&
            isStopPriceValid() &&
            isPairValid()

    private fun isBasicValid(isApiInitialized: Boolean): Boolean =
        selectedInstrument != null &&
            quantity.toLongOrNull()?.let { it > 0 } == true &&
            isApiInitialized

    private fun isLimitPriceValid(): Boolean =
        when (orderType) {
            is OrderTypeSelection.Limit, is OrderTypeSelection.StopLimit ->
                limitPrice.toDoubleOrNull() != null
            else -> true
        }

    private fun isStopPriceValid(): Boolean =
        when (orderType) {
            is OrderTypeSelection.StopLoss,
            is OrderTypeSelection.TakeProfit,
            is OrderTypeSelection.StopLimit,
            ->
                stopPrice.toDoubleOrNull()?.let { it > 0 } == true
            else -> true
        }

    private fun isPairValid(): Boolean =
        if (pairTradingEnabled) {
            pairedInstrument != null &&
                pairedMultiplier.toDoubleOrNull()?.let { it > 0.0 } == true
        } else {
            true
        }

    val quantityAsLong: Long? get() = quantity.toLongOrNull()
}
