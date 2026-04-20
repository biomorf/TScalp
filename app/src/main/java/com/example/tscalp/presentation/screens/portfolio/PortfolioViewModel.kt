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

                // Пробуем разные способы получения позиций
                val positionsList: List<*> = try {
                    portfolio.javaClass.getMethod("getPositionsList").invoke(portfolio) as List<*>
                } catch (e: Exception) {
                    try {
                        val field = portfolio.javaClass.getDeclaredField("positionsList")
                        field.isAccessible = true
                        field.get(portfolio) as List<*>
                    } catch (e2: Exception) {
                        try {
                            val field = portfolio.javaClass.getDeclaredField("positions")
                            field.isAccessible = true
                            field.get(portfolio) as List<*>
                        } catch (e3: Exception) {
                            emptyList<Any>()
                        }
                    }
                }

                Log.d(TAG, "Получено позиций: ${positionsList.size}")

                val positions = positionsList.mapNotNull { position ->
                    position?.let {
                        try {
                            // Пробуем получить FIGI
                            val figi: String = try {
                                it.javaClass.getMethod("getFigi").invoke(it) as String
                            } catch (e: Exception) {
                                val field = it.javaClass.getDeclaredField("figi")
                                field.isAccessible = true
                                field.get(it) as String
                            }

                            // Получаем инструмент
                            val instrument = try {
                                apiService.getInstrumentByFigi(figi)
                            } catch (e: Exception) {
                                Log.w(TAG, "Не удалось получить инструмент для FIGI: $figi")
                                null
                            }

                            if (instrument != null) {
                                PortfolioPosition(
                                    figi = figi,
                                    name = instrument.name,
                                    ticker = instrument.ticker,
                                    quantity = 0L,
                                    currentPrice = 0.0,
                                    totalValue = 0.0
                                )
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка обработки позиции", e)
                            null
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        positions = positions,
                        totalValue = 0.0,
                        isLoading = false,
                        statusMessage = if (positions.isEmpty()) {
                            "Портфель пуст или не удалось загрузить позиции"
                        } else {
                            "Загружено ${positions.size} позиций"
                        },
                        isError = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки портфеля", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Ошибка загрузки портфеля: ${e.message}",
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