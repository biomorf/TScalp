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

/**
 * UI-состояние экрана настроек.
 */
data class SettingsUiState(
    val isSandboxMode: Boolean = true,
    val isConfirmOrdersEnabled: Boolean = true,
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
                isConfirmOrdersEnabled = settingsRepository.isConfirmOrdersEnabled()
            )
        }
    }

    // ---------- Список брокеров ----------

    fun getAvailableBrokers(): List<String> =
        brokerManager.getAvailableBrokers()

    // ---------- Учётные данные брокеров ----------

    fun saveBrokerCredentials(brokerName: String, token: String, sandbox: Boolean) {
        settingsRepository.saveBrokerCredentials(brokerName, token, sandbox)
    }

    fun loadBrokerCredentials(brokerName: String): Pair<String, Boolean>? =
        settingsRepository.loadBrokerCredentials(brokerName)

    fun clearBrokerCredentials(brokerName: String) {
        settingsRepository.clearBrokerCredentials(brokerName)
    }

    fun saveToken(brokerName: String, token: String) {
        settingsRepository.saveToken(brokerName, token)
    }

    fun getToken(brokerName: String): String? =
        settingsRepository.getToken(brokerName)

    fun hasSavedToken(brokerName: String): Boolean =
        settingsRepository.hasSavedToken(brokerName)

    // ---------- Счёт по умолчанию ----------

    fun saveDefaultAccountId(brokerName: String, accountId: String) {
        settingsRepository.saveDefaultAccountId(brokerName, accountId)
    }

    fun loadDefaultAccountId(brokerName: String): String? =
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

    suspend fun getAccounts(brokerName: String, sandboxMode: Boolean): List<BrokerAccount> =
        repository.getAccounts(brokerName, sandboxMode)

    // ---------- Инициализация брокеров ----------

    fun initializeTInvest(token: String, sandbox: Boolean) {
        val cleanToken = token.trim()
        try {
            settingsRepository.saveBrokerCredentials("TInvest", cleanToken, sandbox)
            (brokerManager.getBroker("TInvest") as? TInvestBrokerAPI)
                ?.initialize(cleanToken, sandbox)
            _uiState.update {
                it.copy(
                    statusMessage = "Подключено к Т‑Инвестициям (режим " +
                            "${if (sandbox) "песочница" else "боевой"})",
                    isError = false
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(statusMessage = "Ошибка подключения: ${e.message}", isError = true)
            }
        }
    }

    fun initializeBcs(refreshToken: String, isWriteMode: Boolean) {
        viewModelScope.launch {
            try {
                val clientId = if (isWriteMode) "trade-api-write" else "trade-api-read"
                (brokerManager.getBroker("bcs") as? BcsBrokerApi)?.initialize(refreshToken, clientId)
                settingsRepository.saveBrokerCredentials("bcs", refreshToken, isWriteMode)
                _uiState.update {
                    it.copy(
                        statusMessage = "Подключено к БКС " +
                                "(${if (isWriteMode) "полный доступ" else "только чтение"})",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusMessage = "Ошибка подключения: ${e.message}", isError = true)
                }
            }
        }
    }

    fun initializeFinam(token: String) {
        viewModelScope.launch {
            try {
                settingsRepository.saveToken("finam", token)
                (brokerManager.getBroker("finam") as? FinamBrokerApi)?.initialize(token)
                _uiState.update {
                    it.copy(statusMessage = "Подключено к Finam", isError = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusMessage = "Ошибка подключения: ${e.message}", isError = true)
                }
            }
        }
    }

    // ---------- Операции со счётом песочницы ----------
    // Бросают исключение при ошибке — обработка остаётся на стороне вызова (панели).

    suspend fun openSandboxAccount(): String {
        val broker = brokerManager.getBroker("TInvest") as? TInvestBrokerAPI
            ?: throw IllegalStateException("Брокер TInvest не найден")
        return broker.openSandboxAccount()
    }

    suspend fun closeSandboxAccount(accountId: String) {
        val broker = brokerManager.getBroker("TInvest") as? TInvestBrokerAPI
            ?: throw IllegalStateException("Брокер TInvest не найден")
        broker.closeSandboxAccount(accountId)
    }

    // ---------- Вспомогательные ----------

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, isError = false) }
    }
}
