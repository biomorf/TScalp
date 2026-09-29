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
        if (brokerName == BrokerName.TINVEST.key) {
            settingsRepository.clearTradingState(brokerName)
        }
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

    suspend fun initializeTInvest(token: String, sandbox: Boolean): Boolean {
        val cleanToken = token.trim()
        return try {
            settingsRepository.saveBrokerCredentials(BrokerName.TINVEST.key, cleanToken, sandbox)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                (brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI)
                    ?.initialize(cleanToken, sandbox)
            }
            _uiState.update {
                it.copy(
                    statusMessage = "Подключено к Т‑Инвестициям (режим " +
                            "${if (sandbox) "песочница" else "боевой"})",
                    isError = false
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
            settingsRepository.saveBrokerCredentials(BrokerName.BCS.key, cleanToken, isWriteMode)
            _uiState.update {
                it.copy(
                    statusMessage = "Подключено к БКС " +
                            "(${if (isWriteMode) "полный доступ" else "только чтение"})",
                    isError = false
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
            settingsRepository.saveToken(BrokerName.FINAM.key, cleanToken)
            (brokerManager.getBroker(BrokerName.FINAM) as? FinamBrokerApi)?.initialize(cleanToken)
            _uiState.update {
                it.copy(statusMessage = "Подключено к Finam", isError = false)
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

    suspend fun openSandboxAccount(): String {
        val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI
            ?: throw IllegalStateException("Брокер TInvest не найден")
        return broker.openSandboxAccount()
    }

    suspend fun closeSandboxAccount(accountId: String) {
        val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI
            ?: throw IllegalStateException("Брокер TInvest не найден")
        broker.closeSandboxAccount(accountId)
    }

    // ---------- Вспомогательные ----------

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, isError = false) }
    }
}
