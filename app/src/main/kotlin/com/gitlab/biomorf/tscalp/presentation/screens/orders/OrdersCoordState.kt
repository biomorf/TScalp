package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo

/**
 * Состояние Coordinator-VM (13d-F-A): accounts + lifecycle + cards.
 *
 * После 13d-E-B Form-поля (instrument / quantity / orderType / prices /
 * pair / confirm / status postOrder) переехали в `OrderFormState`.
 * После 13d-F-A `OrdersUiState` переименован в `OrdersCoordState`:
 * - [accounts] / [selectedAccountId] — `loadAccounts` (Coord).
 * - [isApiInitialized] — `checkApiInitialization` (Coord).
 * - [lastSelectedInstruments] — cards identity; Coord реактивен через
 *   `CardsRepository.identities` + `RefreshSelectedInstrumentsUseCase`.
 * - [portfolioPositions] — активный счёт (Coord), для enrich карточек.
 * - [isLoading] / [statusMessage] / [isError] — Coord-level
 *   (`loadAccounts` / `retryLoadAccounts`); Form-level — в `OrderFormState`.
 *
 * **Не содержит** Form-полей (см. `OrderFormState`):
 * - instrument / quantity / orderType / prices / expiry / pair / confirm;
 * - `tradingStatuses` (passthrough в `OrdersViewModel.tradingStatuses`);
 * - `selectedTicker` / `isFormValid` / `quantityAsLong` (computed в
 *   `OrderFormState`);
 * - `activeCard` / `activePairedCard` (extensions ниже, 2c-фиксация).
 */
data class OrdersCoordState(
    val accounts: List<BrokerAccount> = emptyList(),
    val selectedAccountId: String? = null,
    val isApiInitialized: Boolean = false,
    val lastSelectedInstruments: List<SelectedInstrumentInfo> = emptyList(),
    val portfolioPositions: List<Position> = emptyList(),
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
) {
    fun clearStatus(): OrdersCoordState = copy(statusMessage = null, isError = false)
}

/**
 * Per-card `SelectedInstrumentInfo` для основного инструмента.
 *
 * 2c (clean-audit M013 / A2, 2026-11-07): `activeCard` переехал из
 * `OrdersUiState` (где был computed-свойством) в **pure extension**
 * на [OrdersCoordState] с параметром [instrument] (Form-`selectedInstrument`).
 * **Правило Clean:** cross-VM (Form `selectedInstrument` + Coord
 * `lastSelectedInstruments`). **Clean-альтернатива:** параметр [instrument];
 * UI: `coordState.activeCardFor(formState.selectedInstrument)`.
 * **Выгоды:** жёсткая централизация; Coord — владелец
 * `lastSelectedInstruments`, Form — `selectedInstrument`. UI передаёт
 * instrument явно, VM друг друга не читают.
 */
fun OrdersCoordState.activeCardFor(
    instrument: Instrument?,
    accountId: String?,
): SelectedInstrumentInfo? =
    instrument?.let { inst ->
        lastSelectedInstruments.find {
            it.instrument.tscalpInstrumentId == inst.tscalpInstrumentId &&
                it.instrument.brokerName == inst.brokerName &&
                it.accountId == accountId
        }
    }

/**
 * Per-card `SelectedInstrumentInfo` для парного инструмента — аналог
 * [activeCardFor] для Form-`pairedInstrument` (2c-фиксация).
 */
fun OrdersCoordState.activePairedCardFor(
    instrument: Instrument?,
    accountId: String?,
): SelectedInstrumentInfo? =
    instrument?.let { inst ->
        lastSelectedInstruments.find {
            it.instrument.tscalpInstrumentId == inst.tscalpInstrumentId &&
                it.instrument.brokerName == inst.brokerName &&
                it.accountId == accountId
        }
    }
