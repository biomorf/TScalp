package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.usecases.DisconnectBrokerUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.InitializeBrokerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние Finam-панели настроек.
 */
data class FinamUiState(
    val token: String = "",
    val connected: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
)

/**
 * ViewModel Finam-панели настроек (clean-audit M006, A2).
 *
 * Тонкая: state + delegation в usecase + `_uiState.update`.
 * `connected` — реактивно через
 * [BrokerCredentialsRepository.hasSavedTokenFlow] (B2).
 * `token` — `init { loadBrokerCredentials }` (внешних writers нет).
 */
@HiltViewModel
class FinamSettingsViewModel
    @Inject
    constructor(
        private val initializeBrokerUseCase: InitializeBrokerUseCase,
        private val disconnectBrokerUseCase: DisconnectBrokerUseCase,
        private val brokerCredentialsRepository: BrokerCredentialsRepository,
        private val brokerRegistry: BrokerRegistry,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FinamUiState())
        val uiState: StateFlow<FinamUiState> = _uiState.asStateFlow()

        companion object {
            private const val TAG = "FinamSettingsViewModel"
        }

        init {
            viewModelScope.launch {
                logger.d(TAG, "subscribe hasSavedTokenFlow")
                brokerCredentialsRepository.hasSavedTokenFlow(BrokerName.FINAM).collect { has ->
                    _uiState.update { it.copy(connected = has) }
                }
            }
            viewModelScope.launch {
                val saved = brokerCredentialsRepository.getToken(BrokerName.FINAM)
                if (saved != null) {
                    _uiState.update { it.copy(token = saved) }
                }
            }
        }

        /** Подключение (fire-and-forget). */
        fun connect(token: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                logger.d(TAG, "connect")
                when (
                    val r = initializeBrokerUseCase.execute(BrokerCredentials.Finam(token.trim()))
                ) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "Подключено к Finam",
                                isError = false,
                            )
                        }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "Ошибка подключения: ${r.error.message}",
                                isError = true,
                            )
                        }
                }
            }
        }

        /** Отключение (fire-and-forget). */
        fun disconnect() {
            viewModelScope.launch {
                when (val r = disconnectBrokerUseCase.execute(BrokerName.FINAM)) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                token = "",
                                statusMessage = "Подключение к Finam разорвано",
                                isError = false,
                            )
                        }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(
                                statusMessage = "Ошибка отключения: ${r.error.message}",
                                isError = true,
                            )
                        }
                }
            }
        }

        /** Обновление поля токена (UI-ввод). */
        fun setToken(text: String) {
            _uiState.update { it.copy(token = text) }
        }

        /** Сброс statusMessage / isError. */
        fun clearStatus() {
            _uiState.update { it.copy(statusMessage = null, isError = false) }
        }
    }
