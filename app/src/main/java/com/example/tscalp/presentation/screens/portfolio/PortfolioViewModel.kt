package com.example.tscalp.presentation.screens.portfolio

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.tscalp.data.api.TinkoffInvestService
import com.example.tscalp.data.repository.InvestRepository
import com.example.tscalp.domain.models.PortfolioPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PortfolioUiState(
    val positions: List<PortfolioPosition> = emptyList(),
    val totalValue: Double = 0.0,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isApiInitialized: Boolean = false
)

class PortfolioViewModel(
    private val apiService: TinkoffInvestService
) : ViewModel() {

    companion object {
        private const val TAG = "PortfolioViewModel"
    }

    private val repository = InvestRepository(apiService)

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    init {
        checkApiInitialization()
    }

    private fun checkApiInitialization() {
        _uiState.update {
            it.copy(isApiInitialized = apiService.isInitialized)
        }
        if (apiService.isInitialized) {
            loadPortfolio()
        }
    }

    fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                Log.d(TAG, "Загрузка портфеля")

                val accounts = repository.getAccounts()
                if (accounts.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = "Нет доступных счетов",
                            isError = true
                        )
                    }
                    return@launch
                }

                val accountId = accounts.first().id
                Log.d(TAG, "Используем счёт: $accountId")

                val portfolio = apiService.getPortfolio(accountId)
                Log.d(TAG, "Получено позиций: ${portfolio.positions.size}")

                val positions = portfolio.positions.mapNotNull { position ->
                    try {
                        val instrument = apiService.getInstrumentByFigi(position.figi)

                        PortfolioPosition(
                            figi = position.figi,
                            name = instrument.name,
                            ticker = instrument.ticker,
                            quantity = 0L,
                            currentPrice = 0.0,
                            totalValue = 0.0,
                            profit = 0.0,
                            profitPercent = 0.0
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка получения инструмента ${position.figi}", e)
                        null
                    }
                }

                _uiState.update {
                    it.copy(
                        positions = positions,
                        totalValue = 0.0,
                        isLoading = false,
                        statusMessage = "Загружено ${positions.size} позиций (без цен)",
                        isError = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки портфеля", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Ошибка: ${e.message}",
                        isError = true
                    )
                }
            }
        }
    }

    fun refresh() {
        if (apiService.isInitialized) {
            loadPortfolio()
        } else {
            checkApiInitialization()
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, isError = false) }
    }
}

class PortfolioViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PortfolioViewModel::class.java)) {
            val apiService = TinkoffInvestService(context)
            @Suppress("UNCHECKED_CAST")
            return PortfolioViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}