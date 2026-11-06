package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.usecases.CloseSandboxAccountUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.DisconnectBrokerUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.InitializeBrokerUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.LoadBrokerAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.OpenSandboxAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Состояние TInvest-панели настроек.
 *
 * `isConnected` — getter: панель «подключена», если есть
 * сохранённый токен ([connected]) **и** хотя бы один брокер
 * инициализирован ([anyApiInitialized]). До M006 это было
 * локальной `val` в Composable.
 */
data class TInvestUiState(
    val connected: Boolean = false,
    val anyApiInitialized: Boolean = false,
    val sandboxMode: Boolean = true,
    val availableAccounts: List<BrokerAccount> = emptyList(),
    val defaultAccountId: String = "",
    val isRefreshing: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
) {
    val isConnected: Boolean get() = anyApiInitialized && connected
}

/**
 * ViewModel TInvest-панели настроек (clean-audit M006, A2).
 *
 * Тонкая: только state + delegation в usecase + `_uiState.update`.
 * Никакой orchestration (M2). Все business-правила — в usecase
 * ([LoadBrokerAccountsUseCase], [OpenSandboxAccountUseCase],
 * [CloseSandboxAccountUseCase], [DisconnectBrokerUseCase],
 * [InitializeBrokerUseCase]).
 *
 * `connected` — реактивно через [BrokerCredentialsRepository.hasSavedTokenFlow]
 * (B2): `restore*` в `TScalpApplication` обновляет flow, VM видит.
 *
 * `anyApiInitialized` — через [BrokerRegistry.anyInitialized].
 *
 * `sandboxMode` — `init { isSandboxMode() }`: внешних writers нет
 * (только эта VM + `SaveBrokerCredentials` из connect-а).
 */
@HiltViewModel
class TInvestSettingsViewModel
    @Inject
    constructor(
        private val initializeBrokerUseCase: InitializeBrokerUseCase,
        private val disconnectBrokerUseCase: DisconnectBrokerUseCase,
        private val loadBrokerAccountsUseCase: LoadBrokerAccountsUseCase,
        private val openSandboxAccountUseCase: OpenSandboxAccountUseCase,
        private val closeSandboxAccountUseCase: CloseSandboxAccountUseCase,
        private val accountPreferencesRepository: AccountPreferencesRepository,
        private val brokerCredentialsRepository: BrokerCredentialsRepository,
        private val sandboxPreferencesRepository: SandboxPreferencesRepository,
        private val brokerRegistry: BrokerRegistry,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(TInvestUiState())
        val uiState: StateFlow<TInvestUiState> = _uiState.asStateFlow()

        companion object {
            private const val TAG = "TInvestSettingsViewModel"
        }

        init {
            viewModelScope.launch {
                logger.d(TAG, "subscribe anyInitialized")
                brokerRegistry.anyInitialized.collect { isInit ->
                    _uiState.update { it.copy(anyApiInitialized = isInit) }
                }
            }
            viewModelScope.launch {
                logger.d(TAG, "subscribe hasSavedTokenFlow")
                brokerCredentialsRepository.hasSavedTokenFlow(BrokerName.TINVEST).collect { has ->
                    _uiState.update { it.copy(connected = has) }
                }
            }
            viewModelScope.launch {
                val sandbox = sandboxPreferencesRepository.isSandboxMode()
                _uiState.update { it.copy(sandboxMode = sandbox) }
            }
        }

        /**
         * Загружает сохранённые credentials (для инициализации
         * локальных полей ввода в UI). Не state — возврат.
         */
        suspend fun loadCredentials(): Pair<String, Boolean>? = brokerCredentialsRepository.loadBrokerCredentials(BrokerName.TINVEST)

        /**
         * Загружает счета (fire-and-forget). UI вызывает из
         * `LaunchedEffect(isConnected, sandboxMode)`.
         *
         * Если не подключено — очищает список (как было в UI до M006).
         */
        fun loadAccounts() {
            viewModelScope.launch {
                val state = _uiState.value
                if (!state.isConnected) {
                    _uiState.update { it.copy(availableAccounts = emptyList(), defaultAccountId = "") }
                    return@launch
                }
                when (val r = loadBrokerAccountsUseCase.execute(BrokerName.TINVEST, state.sandboxMode)) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                availableAccounts = r.data.accounts,
                                defaultAccountId = r.data.defaultAccountId,
                            )
                        }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(
                                availableAccounts = emptyList(),
                                statusMessage = "Ошибка загрузки счетов: ${r.error.message}",
                                isError = true,
                            )
                        }
                }
            }
        }

        /** Подключение к TInvest (fire-and-forget). */
        fun connect(
            token: String,
            sandboxMode: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                logger.d(TAG, "connect: sandbox=$sandboxMode")
                when (
                    val r = initializeBrokerUseCase.execute(BrokerCredentials.TInvest(token.trim(), sandboxMode))
                ) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                statusMessage =
                                    "Подключено к Т-Инвестициям (режим " +
                                        "${if (sandboxMode) "песочница" else "боевой"})",
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
                when (val r = disconnectBrokerUseCase.execute(BrokerName.TINVEST)) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                availableAccounts = emptyList(),
                                defaultAccountId = "",
                                statusMessage = "Отключено",
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

        /** Смена режима sandbox (fire-and-forget). */
        fun setSandboxMode(enabled: Boolean) {
            viewModelScope.launch {
                sandboxPreferencesRepository.setSandboxMode(enabled)
                _uiState.update { it.copy(sandboxMode = enabled) }
                loadAccounts()
            }
        }

        /** Открытие счёта песочницы (fire-and-forget). */
        fun openSandboxAccount() {
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true) }
                when (
                    val r = openSandboxAccountUseCase.execute(BrokerName.TINVEST, _uiState.value.sandboxMode)
                ) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                defaultAccountId = r.data.accountId,
                                availableAccounts = r.data.accounts ?: it.availableAccounts,
                                statusMessage = "Новый счёт песочницы открыт (ID: ${r.data.accountId})",
                                isError = false,
                            )
                        }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                statusMessage = "Ошибка открытия счёта: ${r.error.message}",
                                isError = true,
                            )
                        }
                }
            }
        }

        /** Закрытие счёта песочницы (fire-and-forget). */
        fun closeSandboxAccount(accountId: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true) }
                when (
                    val r = closeSandboxAccountUseCase.execute(BrokerName.TINVEST, _uiState.value.sandboxMode, accountId)
                ) {
                    is AppResult.Success ->
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                defaultAccountId = "",
                                availableAccounts = r.data.accounts ?: it.availableAccounts,
                                statusMessage = "Счёт песочницы закрыт",
                                isError = false,
                            )
                        }
                    is AppResult.Failure ->
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                statusMessage = "Ошибка закрытия счёта: ${r.error.message}",
                                isError = true,
                            )
                        }
                }
            }
        }

        /** Выбор счёта по умолчанию (fire-and-forget, тонкий — save + state). */
        fun onAccountSelected(accountId: String) {
            viewModelScope.launch {
                accountPreferencesRepository.saveDefaultAccountId(BrokerName.TINVEST, accountId)
                _uiState.update { it.copy(defaultAccountId = accountId) }
            }
        }

        /** Сброс statusMessage / isError. */
        fun clearStatus() {
            _uiState.update { it.copy(statusMessage = null, isError = false) }
        }
    }
