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
 * (M013 / A2, шаг 12a). Семантика Dialog-методов 1:1 с прежними.
 *
 * **Фаза 1b:** `openBrokerDialog(instrument, currentAccountId)` —
 * `currentAccountId` = accountId слота (какой счёт у карточки сейчас).
 * Default `null` — для обратной совместимости UI-callsites до 1b-5.
 * `dialogOldAccountId` заполняется из `OpenCardDialogResult.oldAccountId`;
 * `saveBrokerSettings` → `SaveCardSettingsUseCase.execute(instrument,
 * old, new)`.
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

        fun openBrokerDialog(
            instrument: Instrument,
            currentAccountId: String? = null,
            slot: CardSlot = CardSlot.PRIMARY,
        ) {
            viewModelScope.launch {
                when (val outcome = openCardDialogUseCase.execute(instrument, currentAccountId)) {
                    is OpenCardDialogOutcome.Success -> {
                        val data = outcome.result
                        _state.update {
                            it.copy(
                                showBrokerDialog = true,
                                dialogInstrument = instrument,
                                dialogSlot = slot,
                                dialogOldAccountId = data.oldAccountId,
                                selectedBroker = data.broker,
                                selectedAccountIdDialog = data.selectedAccountId,
                                dialogAccounts = data.accounts,
                                takenAccountIds = data.takenAccountIds,
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
                                dialogSlot = slot,
                                dialogOldAccountId = null,
                                selectedBroker = outcome.broker,
                                selectedAccountIdDialog = null,
                                dialogAccounts = emptyList(),
                                takenAccountIds = emptySet(),
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
                    dialogSlot = CardSlot.PRIMARY,
                    dialogOldAccountId = null,
                    takenAccountIds = emptySet(),
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

        fun saveBrokerSettings(onSaved: (CardSlot, String?) -> Unit = { _, _ -> }) {
            val instrument = _state.value.dialogInstrument ?: return
            val slot = _state.value.dialogSlot
            val oldAccountId = _state.value.dialogOldAccountId
            val newAccountId = _state.value.selectedAccountIdDialog
            viewModelScope.launch {
                when (val result = saveCardSettingsUseCase.execute(instrument, oldAccountId, newAccountId)) {
                    is AppResult.Success -> {
                        logger.d(
                            TAG,
                            "saveBrokerSettings: uid=${instrument.tscalpInstrumentId}, " +
                                "broker=${instrument.brokerName}, " +
                                "oldAccountId=$oldAccountId, newAccountId=$newAccountId",
                        )
                        _state.update { state ->
                            state.copy(
                                showBrokerDialog = false,
                                dialogInstrument = null,
                                dialogSlot = CardSlot.PRIMARY,
                                dialogOldAccountId = null,
                                takenAccountIds = emptySet(),
                                swipeResetTrigger = !state.swipeResetTrigger,
                            )
                        }
                        onSaved(slot, newAccountId)
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
                                dialogSlot = CardSlot.PRIMARY,
                                dialogOldAccountId = null,
                                takenAccountIds = emptySet(),
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
