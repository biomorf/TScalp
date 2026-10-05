package com.example.tscalp.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.tscalp.data.api.TInvestBrokerAPI
import com.example.tscalp.data.api.BcsBrokerApi
import com.example.tscalp.data.api.FinamBrokerApi
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.data.repository.SettingsRepository
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.BrokerAccount
import com.example.tscalp.domain.models.BrokerName
import com.example.tscalp.domain.models.AppResult

/**
 * UI-состояние экрана настроек.
 */
data class SettingsUiState(
    val isSandboxMode: Boolean = true,
    val isConfirmOrdersEnabled: Boolean = true,
    val tInvestConnected: Boolean = false,
    val bcsConnected: Boolean = false,
    val finamConnected: Boolean = false,
    val defaultAccountIdTInvest: String = "",
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val brokerManager: BrokerManager,
    private val repository: InvestRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Первичная синхронизация состояния с prefs
        _uiState.update {
            it.copy(
                isSandboxMode = settingsRepository.isSandboxMode(),
                isConfirmOrdersEnabled = settingsRepository.isConfirmOrdersEnabled(),
                tInvestConnected = settingsRepository.hasSavedToken(BrokerName.TINVEST),
                bcsConnected = settingsRepository.hasSavedToken(BrokerName.BCS),
                finamConnected = settingsRepository.hasSavedToken(BrokerName.FINAM),
                defaultAccountIdTInvest = settingsRepository.loadDefaultAccountId(BrokerName.TINVEST) ?: ""
            )
        }
    }

    // ---------- Список брокеров ----------

    fun getAvailableBrokerNames(): List<BrokerName> =
        brokerManager.getAvailableBrokerNames()

    // ---------- Учётные данные брокеров ----------

    fun saveBrokerCredentials(brokerName: BrokerName, token: String, sandbox: Boolean) {
        settingsRepository.saveBrokerCredentials(brokerName, token, sandbox)
    }

    fun loadBrokerCredentials(brokerName: BrokerName): Pair<String, Boolean>? =
        settingsRepository.loadBrokerCredentials(brokerName)

    fun clearBrokerCredentials(brokerName: BrokerName) {
        settingsRepository.clearBrokerCredentials(brokerName)
        if (brokerName == BrokerName.TINVEST) {
            settingsRepository.clearTradingState()
        }
        _uiState.update {
            when (brokerName) {
                BrokerName.TINVEST -> it.copy(
                    tInvestConnected = false,
                    defaultAccountIdTInvest = ""
                )
                BrokerName.BCS -> it.copy(bcsConnected = false)
                BrokerName.FINAM -> it.copy(finamConnected = false)
            }
        }
    }

    fun saveToken(brokerName: BrokerName, token: String) {
        settingsRepository.saveToken(brokerName, token)
    }

    fun getToken(brokerName: BrokerName): String? =
        settingsRepository.getToken(brokerName)

    fun hasSavedToken(brokerName: BrokerName): Boolean =
        settingsRepository.hasSavedToken(brokerName)

    // ---------- Счёт по умолчанию ----------

    fun saveDefaultAccountId(brokerName: BrokerName, accountId: String) {
        settingsRepository.saveDefaultAccountId(brokerName, accountId)
        if (brokerName == BrokerName.TINVEST) {
            _uiState.update { it.copy(defaultAccountIdTInvest = accountId) }
        }
    }

    fun loadDefaultAccountId(brokerName: BrokerName): String? =
        settingsRepository.loadDefaultAccountId(brokerName)

    // ---------- Режим песочницы ----------

    fun isSandboxMode(): Boolean = settingsRepository.isSandboxMode()

    fun setSandboxMode(enabled: Boolean) {
        settingsRepository.setSandboxMode(enabled)
        _uiState.update { it.copy(isSandboxMode = enabled) }
    }

    // ---------- Подтверждение заявок ----------

    fun isConfirmOrdersEnabled(): Boolean =
        settingsRepository.isConfirmOrdersEnabled()

    fun setConfirmOrdersEnabled(enabled: Boolean) {
        settingsRepository.setConfirmOrdersEnabled(enabled)
        _uiState.update { it.copy(isConfirmOrdersEnabled = enabled) }
    }

    // ---------- Счета ----------

    /**
     * Список счетов брокера.
     * Типизированный результат: AppResult.Success со списком или AppResult.Failure.
     */
    suspend fun getAccountsResult(
        brokerName: BrokerName,
        sandboxMode: Boolean
    ): AppResult<List<BrokerAccount>> =
        repository.getAccountsResult(brokerName, sandboxMode)


    // ---------- Инициализация брокеров ----------

    suspend fun initializeTInvest(token: String, sandbox: Boolean): Boolean {
        val cleanToken = token.trim()
        return try {
            settingsRepository.saveBrokerCredentials(BrokerName.TINVEST, cleanToken, sandbox)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                (brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI)
                    ?.initialize(cleanToken, sandbox)
            }
            _uiState.update {
                it.copy(
                    statusMessage = "Подключено к Т‑Инвестициям (режим " +
                            "${if (sandbox) "песочница" else "боевой"})",
                    isError = false,
                    tInvestConnected = true
                )
            }
            true
        } catch (e: Exception) {
            _uiState.update {
                it.copy(statusMessage = "Ошибка подключения: ${e.message}", isError = true)
            }
            false
        }
    }

    suspend fun initializeBcs(refreshToken: String, isWriteMode: Boolean): Boolean {
        val cleanToken = refreshToken.trim()
        return try {
            val clientId = if (isWriteMode) "trade-api-write" else "trade-api-read"
            (brokerManager.getBroker(BrokerName.BCS) as? BcsBrokerApi)?.initialize(cleanToken, clientId)
            settingsRepository.saveBrokerCredentials(BrokerName.BCS, cleanToken, isWriteMode)
            _uiState.update {
                it.copy(
                    statusMessage = "Подключено к БКС " +
                            "(${if (isWriteMode) "полный доступ" else "только чтение"})",
                    isError = false,
                    bcsConnected = true
                )
            }
            true
        } catch (e: Exception) {
            _uiState.update {
                it.copy(statusMessage = "Ошибка подключения: ${e.message}", isError = true)
            }
            false
        }
    }

    suspend fun initializeFinam(token: String): Boolean {
        val cleanToken = token.trim()
        return try {
            settingsRepository.saveToken(BrokerName.FINAM, cleanToken)
            (brokerManager.getBroker(BrokerName.FINAM) as? FinamBrokerApi)?.initialize(cleanToken)
            _uiState.update {
                it.copy(
                    statusMessage = "Подключено к Finam",
                    isError = false,
                    finamConnected = true
                )
            }
            true
        } catch (e: Exception) {
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
    suspend fun openSandboxAccountResult(): AppResult<String> =
        repository.openSandboxAccountResult(BrokerName.TINVEST)

    /**
     * Закрывает счёт в песочнице TInvest.
     * Возвращает AppResult: Success(Unit) или Failure с причиной.
     */
    suspend fun closeSandboxAccountResult(accountId: String): AppResult<Unit> =
        repository.closeSandboxAccountResult(BrokerName.TINVEST, accountId)

    // ---------- Вспомогательные ----------

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, isError = false) }
    }
}
