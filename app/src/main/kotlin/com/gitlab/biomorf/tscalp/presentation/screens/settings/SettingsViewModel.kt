package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.api.managers.OrderFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SettingsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI-состояние экрана настроек.
 */
data class SettingsUiState(
    val isSandboxMode: Boolean = true,
    val isConfirmOrdersEnabled: Boolean = true,
    val tInvestConnected: Boolean = false,
    val bcsConnected: Boolean = false,
    val finamConnected: Boolean = false,
    val anyApiInitialized: Boolean = false,
    val defaultAccountIdTInvest: String = "",
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    // Debug-флаги
    val disableGrpc: Boolean = false,
    val disableWs: Boolean = false,
    val forceRest: Boolean = false,
)

// Архитектурный долг: 20 функций в классе. См. ROADMAP → «Рефакторинг ViewModel-слоя».
@Suppress("TooManyFunctions")
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val tradingStateRepository: TradingStateRepository,
        private val brokerManager: BrokerRegistry,
        private val positionFlowManager: PositionFlowManager,
        private val priceFlowManager: PriceFlowManager,
        private val orderFlowManager: OrderFlowManager,
        private val accountsRepository: AccountsRepository,
        private val debugFlagsRepository: DebugFlagsRepository,
        private val logger: Logger,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        companion object {
            private const val TAG = "SettingsViewModel"
        }

        init {
            // Первичная синхронизация состояния с DataStore.
            // Два launch в одном init, а не в двух отдельных блоках:
            // оба относятся к первичной синхронизации _uiState
            // (данные DataStore + реактивный флаг anyApiInitialized).
            // Раньше было два init, и при добавлении подписки на
            // anyInitialized первый launch скопировали целиком — см.
            // docs/ISSUES.md → «Дублирующий init {} в SettingsViewModel».
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isSandboxMode = settingsRepository.isSandboxMode(),
                        isConfirmOrdersEnabled = settingsRepository.isConfirmOrdersEnabled(),
                        tInvestConnected = settingsRepository.hasSavedToken(BrokerName.TINVEST),
                        bcsConnected = settingsRepository.hasSavedToken(BrokerName.BCS),
                        finamConnected = settingsRepository.hasSavedToken(BrokerName.FINAM),
                        defaultAccountIdTInvest = settingsRepository.loadDefaultAccountId(BrokerName.TINVEST) ?: "",
                        disableGrpc = debugFlagsRepository.disableGrpc,
                        disableWs = debugFlagsRepository.disableWs,
                        forceRest = debugFlagsRepository.forceRest,
                    )
                }
            }
            // Реактивный флаг «есть ли хотя бы один инициализированный брокер»
            viewModelScope.launch {
                brokerManager.anyInitialized.collect { isInit ->
                    logger.d(TAG, "anyInitialized = $isInit")
                    _uiState.update { it.copy(anyApiInitialized = isInit) }
                }
            }
        }

        // ---------- Список брокеров ----------

        fun getAvailableBrokerNames(): List<BrokerName> = brokerManager.getAvailableBrokerNames()

        // ---------- Учётные данные брокеров ----------

        suspend fun saveBrokerCredentials(
            brokerName: BrokerName,
            token: String,
            sandbox: Boolean,
        ) {
            settingsRepository.saveBrokerCredentials(brokerName, token, sandbox)
        }

        suspend fun loadBrokerCredentials(brokerName: BrokerName): Pair<String, Boolean>? = settingsRepository.loadBrokerCredentials(brokerName)

        /**
         * Fire-and-forget: вызывается из синхронного onClick в UI.
         * Запись в DataStore асинхронная, результат не нужен.
         */
        fun clearBrokerCredentials(brokerName: BrokerName) {
            viewModelScope.launch {
                logger.d(TAG, "clearBrokerCredentials: $brokerName")
                settingsRepository.clearBrokerCredentials(brokerName)
                if (brokerName == BrokerName.TINVEST) {
                    tradingStateRepository.clear()
                }

                // Деинициализируем брокера — иначе state.api остаётся non-null,
                // isInitialized возвращает true, и anyInitialized не переключается.
                // lifecycle == null — брокер не поддерживает (no-op).
                when (brokerName) {
                    BrokerName.TINVEST -> brokerManager.getBroker(BrokerName.TINVEST)?.lifecycleTInvest?.deinitialize()
                    BrokerName.BCS -> brokerManager.getBroker(BrokerName.BCS)?.lifecycleBcs?.deinitialize()
                    BrokerName.FINAM -> brokerManager.getBroker(BrokerName.FINAM)?.lifecycleFinam?.deinitialize()
                }

                // Останавливаем общий стрим позиций сразу, не дожидаясь,
                // будет ли создан PortfolioViewModel.
                if (brokerName == BrokerName.TINVEST) {
                    positionFlowManager.stop()
                    orderFlowManager.stop()
                }

                brokerManager.refreshInitializationState()

                _uiState.update {
                    when (brokerName) {
                        BrokerName.TINVEST ->
                            it.copy(
                                tInvestConnected = false,
                                defaultAccountIdTInvest = "",
                            )
                        BrokerName.BCS -> it.copy(bcsConnected = false)
                        BrokerName.FINAM -> it.copy(finamConnected = false)
                    }
                }
                logger.d(TAG, "clearBrokerCredentials: $brokerName deinitialized")
            }
        }

        suspend fun saveToken(
            brokerName: BrokerName,
            token: String,
        ) {
            settingsRepository.saveToken(brokerName, token)
        }

        suspend fun getToken(brokerName: BrokerName): String? = settingsRepository.getToken(brokerName)

        suspend fun hasSavedToken(brokerName: BrokerName): Boolean = settingsRepository.hasSavedToken(brokerName)

        // ---------- Счёт по умолчанию ----------

        fun saveDefaultAccountId(
            brokerName: BrokerName,
            accountId: String,
        ) {
            viewModelScope.launch {
                settingsRepository.saveDefaultAccountId(brokerName, accountId)
                if (brokerName == BrokerName.TINVEST) {
                    _uiState.update { it.copy(defaultAccountIdTInvest = accountId) }
                }
            }
        }

        suspend fun loadDefaultAccountId(brokerName: BrokerName): String? = settingsRepository.loadDefaultAccountId(brokerName)

        // ---------- Режим песочницы ----------

        suspend fun isSandboxMode(): Boolean = settingsRepository.isSandboxMode()

        fun setSandboxMode(enabled: Boolean) {
            viewModelScope.launch {
                logger.d(TAG, "setSandboxMode: $enabled")
                settingsRepository.setSandboxMode(enabled)
                _uiState.update { it.copy(isSandboxMode = enabled) }
            }
        }

        // ---------- Подтверждение заявок ----------

        suspend fun isConfirmOrdersEnabled(): Boolean = settingsRepository.isConfirmOrdersEnabled()

        fun setConfirmOrdersEnabled(enabled: Boolean) {
            viewModelScope.launch {
                settingsRepository.setConfirmOrdersEnabled(enabled)
                _uiState.update { it.copy(isConfirmOrdersEnabled = enabled) }
            }
        }

        // ---------- Счета ----------

        /**
         * Список счетов брокера.
         * Типизированный результат: AppResult.Success со списком или AppResult.Failure.
         */
        suspend fun getAccountsResult(
            brokerName: BrokerName,
            sandboxMode: Boolean,
        ): AppResult<List<BrokerAccount>> {
            val result = accountsRepository.getAccountsResult(brokerName, sandboxMode)
            if (result is AppResult.Failure) {
                logger.w(TAG, "getAccounts($brokerName): ${result.error.message}", result.error.cause)
            }
            return result
        }

        // ---------- Инициализация брокеров ----------

        suspend fun initializeTInvest(
            token: String,
            sandbox: Boolean,
        ): Boolean {
            logger.d(TAG, "initializeTInvest: sandbox=$sandbox")
            val cleanToken = token.trim()
            return try {
                settingsRepository.saveBrokerCredentials(BrokerName.TINVEST, cleanToken, sandbox)
                val initialized =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        brokerManager
                            .getBroker(BrokerName.TINVEST)
                            ?.lifecycleTInvest
                            ?.initialize(BrokerCredentials.TInvest(cleanToken, sandbox)) ?: false
                    }
                if (!initialized) throw IllegalStateException("Инициализация брокера не удалась")
                brokerManager.refreshInitializationState()
                _uiState.update {
                    it.copy(
                        statusMessage =
                            "Подключено к Т‑Инвестициям (режим " +
                                "${if (sandbox) "песочница" else "боевой"})",
                        isError = false,
                        tInvestConnected = true,
                    )
                }
                logger.d(TAG, "initializeTInvest: success")
                true
            } catch (e: Exception) {
                logger.e(TAG, "initializeTInvest failed", e)
                _uiState.update {
                    it.copy(statusMessage = "Ошибка подключения: ${e.message}", isError = true)
                }
                false
            }
        }

        suspend fun initializeBcs(
            refreshToken: String,
            isWriteMode: Boolean,
        ): Boolean {
            logger.d(TAG, "initializeBcs: isWriteMode=$isWriteMode")
            val cleanToken = refreshToken.trim()
            return try {
                val initialized =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        brokerManager
                            .getBroker(BrokerName.BCS)
                            ?.lifecycleBcs
                            ?.initialize(BrokerCredentials.Bcs(cleanToken, isWriteMode)) ?: false
                    }
                if (!initialized) throw IllegalStateException("Инициализация брокера не удалась")
                settingsRepository.saveBrokerCredentials(BrokerName.BCS, cleanToken, isWriteMode)
                brokerManager.refreshInitializationState()
                _uiState.update {
                    it.copy(
                        statusMessage =
                            "Подключено к БКС " +
                                "(${if (isWriteMode) "полный доступ" else "только чтение"})",
                        isError = false,
                        bcsConnected = true,
                    )
                }
                logger.d(TAG, "initializeBcs: success")
                true
            } catch (e: Exception) {
                logger.e(TAG, "initializeBcs failed", e)
                _uiState.update {
                    it.copy(statusMessage = "Ошибка подключения: ${e.message}", isError = true)
                }
                false
            }
        }

        suspend fun initializeFinam(token: String): Boolean {
            logger.d(TAG, "initializeFinam")
            val cleanToken = token.trim()
            return try {
                settingsRepository.saveToken(BrokerName.FINAM, cleanToken)
                val initialized =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        brokerManager
                            .getBroker(BrokerName.FINAM)
                            ?.lifecycleFinam
                            ?.initialize(BrokerCredentials.Finam(cleanToken)) ?: false
                    }
                if (!initialized) throw IllegalStateException("Инициализация брокера не удалась")
                brokerManager.refreshInitializationState()
                _uiState.update {
                    it.copy(
                        statusMessage = "Подключено к Finam",
                        isError = false,
                        finamConnected = true,
                    )
                }
                logger.d(TAG, "initializeFinam: success")
                true
            } catch (e: Exception) {
                logger.e(TAG, "initializeFinam failed", e)
                _uiState.update {
                    it.copy(statusMessage = "Ошибка подключения: ${e.message}", isError = true)
                }
                false
            }
        }

        // ---------- Операции со счётом песочницы ----------
        // Бросают исключение при ошибке — обработка остаётся на стороне вызова (панели).

        /**
         * Открывает новый счёт в песочнице TInvest.
         * Возвращает AppResult: Success(accountId) или Failure с причиной.
         */
        suspend fun openSandboxAccountResult(): AppResult<String> {
            val result = accountsRepository.openSandboxAccountResult(BrokerName.TINVEST)
            if (result is AppResult.Failure) {
                logger.w(TAG, "openSandboxAccount: ${result.error.message}", result.error.cause)
            }
            return result
        }

        /**
         * Закрывает счёт в песочнице TInvest.
         * Возвращает AppResult: Success(Unit) или Failure с причиной.
         */
        suspend fun closeSandboxAccountResult(accountId: String): AppResult<Unit> {
            val result = accountsRepository.closeSandboxAccountResult(BrokerName.TINVEST, accountId)
            if (result is AppResult.Failure) {
                logger.w(TAG, "closeSandboxAccount: ${result.error.message}", result.error.cause)
            }
            return result
        }

        // ---------- Вспомогательные ----------

        fun clearStatus() {
            _uiState.update { it.copy(statusMessage = null, isError = false) }
        }

        fun toggleGrpc(enabled: Boolean) {
            logger.d(TAG, "toggleGrpc: enabled=$enabled")
            debugFlagsRepository.disableGrpc = enabled
            _uiState.update { it.copy(disableGrpc = debugFlagsRepository.disableGrpc) }
            priceFlowManager.reapplyDebugFlags()
            positionFlowManager.reapplyDebugFlags()
            orderFlowManager.reapplyDebugFlags()
        }

        fun toggleWs(enabled: Boolean) {
            logger.d(TAG, "toggleWs: enabled=$enabled")
            debugFlagsRepository.disableWs = enabled
            _uiState.update { it.copy(disableWs = debugFlagsRepository.disableWs) }
            priceFlowManager.reapplyDebugFlags()
            positionFlowManager.reapplyDebugFlags()
            orderFlowManager.reapplyDebugFlags()
        }

        fun toggleRest(enabled: Boolean) {
            logger.d(TAG, "toggleRest: enabled=$enabled")
            debugFlagsRepository.forceRest = enabled
            _uiState.update { it.copy(forceRest = debugFlagsRepository.forceRest) }
            priceFlowManager.reapplyDebugFlags()
            positionFlowManager.reapplyDebugFlags()
            orderFlowManager.reapplyDebugFlags()
        }
    }
