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
 * Состояние BCS-панели настроек.
 */
data class BcsUiState(
    val refreshToken: String = "",
    val isWriteMode: Boolean = true,
    val connected: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
)

/**
 * ViewModel BCS-панели настроек (clean-audit M006, A2).
 *
 * Тонкая: state + delegation в usecase + `_uiState.update`.
 * `connected` — реактивно через
 * [BrokerCredentialsRepository.hasSavedTokenFlow] (B2).
 * `refreshToken` / `isWriteMode` — `init { loadBrokerCredentials }`
 * (внешних writers нет).
 */
@HiltViewModel
class BcsSettingsViewModel
    @Inject
    constructor(
        private val initializeBrokerUseCase: InitializeBrokerUseCase,
        private val disconnectBrokerUseCase: DisconnectBrokerUseCase,
        private val brokerCredentialsRepository: BrokerCredentialsRepository,
        private val brokerRegistry: BrokerRegistry,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(BcsUiState())
        val uiState: StateFlow<BcsUiState> = _uiState.asStateFlow()

        companion object {
            private const val TAG = "BcsSettingsViewModel"
        }

        init {
            viewModelScope.launch {
                logger.d(TAG, "subscribe hasSavedTokenFlow")
                brokerCredentialsRepository.hasSavedTokenFlow(BrokerName.BCS).collect { has ->
                    _uiState.update { it.copy(connected = has) }
                }
            }
            viewModelScope.launch {
                val creds = brokerCredentialsRepository.loadBrokerCredentials(BrokerName.BCS)
                if (creds != null) {
                    _uiState.update { it.copy(refreshToken = creds.first, isWriteMode = creds.second) }
                }
            }
        }

        /** Подключение (fire-and-forget). */
        fun connect(
            refreshToken: String,
            isWriteMode: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                logger.d(TAG, "connect: isWriteMode=$isWriteMode")
                when (
                    val r = initializeBrokerUseCase.execute(BrokerCredentials.Bcs(refreshToken.trim(), isWriteMode))
                ) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage =
                                    "Подключено к БКС (${if (isWriteMode) "полный доступ" else "только чтение"})",
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
                when (val r = disconnectBrokerUseCase.execute(BrokerName.BCS)) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                refreshToken = "",
                                statusMessage = "Подключение к БКС разорвано",
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

        /** Обновление поля refresh-токена (UI-ввод). */
        fun setRefreshToken(text: String) {
            _uiState.update { it.copy(refreshToken = text) }
        }

        /** Переключение прав доступа (read / write). */
        fun setIsWriteMode(isWrite: Boolean) {
            _uiState.update { it.copy(isWriteMode = isWrite) }
        }

        /** Сброс statusMessage / isError. */
        fun clearStatus() {
            _uiState.update { it.copy(statusMessage = null, isError = false) }
        }
    }
