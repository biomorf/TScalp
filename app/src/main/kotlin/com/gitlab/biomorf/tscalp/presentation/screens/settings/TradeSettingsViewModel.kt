package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние панели «Настройки торговли».
 */
data class TradeUiState(
    val isConfirmOrdersEnabled: Boolean = true,
)

/**
 * ViewModel панели «Настройки торговли» (clean-audit M006, A2).
 *
 * Тонкая: state + delegation в [TradingPreferencesRepository].
 * `isConfirmOrdersEnabled` — реактивно через
 * [TradingPreferencesRepository.confirmOrdersEnabledFlow] (уже
 * есть в репозитории, симметрично [com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository.hasSavedTokenFlow]).
 */
@HiltViewModel
class TradeSettingsViewModel
    @Inject
    constructor(
        private val tradingPreferencesRepository: TradingPreferencesRepository,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TradeUiState())
        val uiState: StateFlow<TradeUiState> = _uiState.asStateFlow()

        companion object {
            private const val TAG = "TradeSettingsViewModel"
        }

        init {
            viewModelScope.launch {
                logger.d(TAG, "subscribe confirmOrdersEnabledFlow")
                tradingPreferencesRepository.confirmOrdersEnabledFlow().collect { enabled ->
                    _uiState.update { it.copy(isConfirmOrdersEnabled = enabled) }
                }
            }
        }

        /** Fire-and-forget: сохранение флага + обновление state. */
        fun setConfirmOrdersEnabled(enabled: Boolean) {
            viewModelScope.launch {
                tradingPreferencesRepository.setConfirmOrdersEnabled(enabled)
                _uiState.update { it.copy(isConfirmOrdersEnabled = enabled) }
            }
        }
    }
