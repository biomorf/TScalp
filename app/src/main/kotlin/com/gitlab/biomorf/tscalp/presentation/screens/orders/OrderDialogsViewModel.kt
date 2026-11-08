package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.usecases.LoadCardAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.OpenCardDialogOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.OpenCardDialogUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SaveCardSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel диалога выбора брокера и счёта для карточки инструмента.
 *
 * Вынесено из `OrdersViewModel` в рамках split `OrdersViewModel`
 * (M013 / A2, шаг 12a). Тела методов 1:1 с прежними Dialog-методами
 * Coord'а, кроме двух замен источников:
 * - `_uiState.value.lastSelectedInstruments` ->
 *   `cardsRepository.identities.first()` — эквивалент:
 *   `lastSelectedInstruments` — projection of [CardsRepository.identities]
 *   через A3/A4 [RefreshSelectedInstrumentsUseCase];
 * - `_uiState.value.selectedAccountId` ->
 *   `accountSelectionRepository.selected(TINVEST).first()` — эквивалент:
 *   Coord `selectedAccountId` приходит из `loadOrdersAccountsUseCase`,
 *   чей source — [AccountSelectionRepository.selected] + fallback
 *   `accounts.firstOrNull()?.id`; [LoadCardAccountsUseCase] при
 *   `savedAccountId = null` делает тот же fallback.
 *
 * **Не обновляет** `lastSelectedInstruments` (Coord реагирует на
 * [CardsRepository.identities] через A3) и **не вызывает**
 * `startPositionUpdates` (UI-оркестрация, KDoc в Composable — 12b, P4).
 *
 * **2c (фиксировано):** inject `SandboxPreferencesRepository`
 * (domain.api.repository) — не отклонение: интерфейс в domain-слое
 * после пакета B (H68).
 *
 * **M2 partial (clean-audit M013, L1):** `openBrokerDialog`
 * содержит orchestration (find `existingCard` + `savedAccountId`
 * fallback) — pre-existing M013, не новое (Coord делал то же
 * до 12a). **Правило Clean:** M2 — business-orchestration в VM,
 * не в UseCase. **Clean-альтернатива:** `OpenBrokerDialogUseCase
 * (instrument): AppResult<CardAccountsResult>` — findCard +
 * savedFallback + `LoadCardAccountsUseCase.execute(...)`.
 * **Выгоды:** 1:1 с Coord (минимальный diff 12a);
 * `loadCardAccountsUseCase` уже покрывает основную orchestration
 * (Q10/Q11, ROADMAP 12a); снижение diff-риска в 12b.
 * Чистая альтернатива — отдельная задача (эволюция M013).
 */
@HiltViewModel
class OrderDialogsViewModel
    @Inject
    constructor(
        private val loadCardAccountsUseCase: LoadCardAccountsUseCase,
        private val openCardDialogUseCase: OpenCardDialogUseCase,
        private val saveCardSettingsUseCase: SaveCardSettingsUseCase,
        private val sandboxPreferencesRepository: SandboxPreferencesRepository,
        private val brokerRegistry: BrokerRegistry,
        private val logger: Logger,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderDialogsState())
        val state: StateFlow<OrderDialogsState> = _state.asStateFlow()

        fun openBrokerDialog(instrument: Instrument) {
            viewModelScope.launch {
                when (val outcome = openCardDialogUseCase.execute(instrument)) {
                    is OpenCardDialogOutcome.Success -> {
                        val data = outcome.result
                        _state.update {
                            it.copy(
                                showBrokerDialog = true,
                                dialogInstrument = instrument,
                                selectedBroker = data.broker,
                                selectedAccountIdDialog = data.selectedAccountId,
                                dialogAccounts = data.accounts,
                            )
                        }
                    }
                    is OpenCardDialogOutcome.Failure -> {
                        logger.e(
                            TAG,
                            "Ошибка загрузки счетов для ${outcome.broker.displayName}: ${outcome.error.message}",
                            outcome.error.cause,
                        )
                        _state.update {
                            it.copy(
                                showBrokerDialog = true,
                                dialogInstrument = instrument,
                                selectedBroker = outcome.broker,
                                selectedAccountIdDialog = null,
                                dialogAccounts = emptyList(),
                            )
                        }
                    }
                }
            }
        }

        fun closeBrokerDialog() {
            _state.update {
                it.copy(
                    showBrokerDialog = false,
                    dialogInstrument = null,
                    swipeResetTrigger = !it.swipeResetTrigger,
                )
            }
        }

        fun onBrokerSelected(brokerName: BrokerName) {
            _state.update { it.copy(selectedBroker = brokerName, selectedAccountIdDialog = null) }
            viewModelScope.launch {
                loadDialogAccounts(brokerName)
            }
        }

        fun onAccountSelectedDialog(accountId: String) {
            _state.update { it.copy(selectedAccountIdDialog = accountId) }
        }

        fun saveBrokerSettings() {
            val instrument = _state.value.dialogInstrument ?: return
            val accountId = _state.value.selectedAccountIdDialog
            viewModelScope.launch {
                when (val result = saveCardSettingsUseCase.execute(instrument, accountId)) {
                    is AppResult.Success -> {
                        logger.d(
                            TAG,
                            "saveBrokerSettings: uid=${instrument.tscalpInstrumentId}, " +
                                "broker=${instrument.brokerName}, accountId=$accountId",
                        )
                        _state.update { state ->
                            state.copy(
                                showBrokerDialog = false,
                                dialogInstrument = null,
                                swipeResetTrigger = !state.swipeResetTrigger,
                            )
                        }
                    }
                    is AppResult.Failure -> {
                        logger.e(
                            TAG,
                            "saveBrokerSettings failed: ${result.error.message}",
                            result.error.cause,
                        )
                        _state.update { state ->
                            state.copy(
                                showBrokerDialog = false,
                                dialogInstrument = null,
                                swipeResetTrigger = !state.swipeResetTrigger,
                            )
                        }
                    }
                }
            }
        }

        fun getAvailableBrokerNames(): List<BrokerName> = brokerRegistry.getAvailableBrokerNames()

        private suspend fun loadDialogAccounts(brokerName: BrokerName) {
            val result =
                loadCardAccountsUseCase.execute(
                    brokerName = brokerName,
                    sandboxMode = sandboxPreferencesRepository.isSandboxMode(),
                    savedAccountId = _state.value.selectedAccountIdDialog,
                )
            when (result) {
                is AppResult.Success -> {
                    val data = result.data
                    _state.update {
                        it.copy(
                            dialogAccounts = data.accounts,
                            selectedAccountIdDialog = data.selectedAccountId,
                        )
                    }
                }
                is AppResult.Failure -> {
                    logger.e(
                        TAG,
                        "Ошибка загрузки счетов для $brokerName: ${result.error.message}",
                        result.error.cause,
                    )
                    _state.update { it.copy(dialogAccounts = emptyList(), selectedAccountIdDialog = null) }
                }
            }
        }

        companion object {
            private const val TAG = "OrderDialogsViewModel"
        }
    }
