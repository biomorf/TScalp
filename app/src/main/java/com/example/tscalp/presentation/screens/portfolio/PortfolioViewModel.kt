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

                // Универсальное получение списка позиций
                val positionsList: List<*> = try {
                    // Способ 1: через getPositionsList()
                    portfolio.javaClass.getMethod("getPositionsList").invoke(portfolio) as List<*>
                } catch (e: Exception) {
                    try {
                        // Способ 2: через поле positionsList
                        val field = portfolio.javaClass.getDeclaredField("positionsList")
                        field.isAccessible = true
                        field.get(portfolio) as List<*>
                    } catch (e2: Exception) {
                        try {
                            // Способ 3: через поле positions
                            val field = portfolio.javaClass.getDeclaredField("positions")
                            field.isAccessible = true
                            field.get(portfolio) as List<*>
                        } catch (e3: Exception) {
                            Log.e(TAG, "Не удалось получить список позиций", e3)
                            emptyList<Any>()
                        }
                    }
                }

                Log.d(TAG, "Получено позиций: ${positionsList.size}")

                val positions = positionsList.mapNotNull { position ->
                    position?.let {
                        try {
                            // Извлечение FIGI
                            val figi: String = try {
                                it.javaClass.getMethod("getFigi").invoke(it) as String
                            } catch (e: Exception) {
                                val field = it.javaClass.getDeclaredField("figi")
                                field.isAccessible = true
                                field.get(it) as String
                            }

                            val instrument = apiService.getInstrumentByFigi(figi)

                            // Извлечение количества
                            val quantity: Long = try {
                                val qtyObj = it.javaClass.getMethod("getQuantity").invoke(it)
                                val units = qtyObj.javaClass.getMethod("getUnits").invoke(qtyObj) as Long
                                val nano = qtyObj.javaClass.getMethod("getNano").invoke(qtyObj) as Int
                                units + nano / 1_000_000_000
                            } catch (e: Exception) {
                                Log.w(TAG, "Не удалось извлечь количество для $figi, используется 0")
                                0L
                            }

                            if (quantity == 0L) return@mapNotNull null

                            // Извлечение текущей цены
                            val currentPrice: Double = try {
                                val priceObj = it.javaClass.getMethod("getCurrentPrice").invoke(it)
                                val units = priceObj.javaClass.getMethod("getUnits").invoke(priceObj) as Long
                                val nano = priceObj.javaClass.getMethod("getNano").invoke(priceObj) as Int
                                units + nano / 1_000_000_000.0
                            } catch (e: Exception) {
                                Log.w(TAG, "Не удалось извлечь цену для $figi, используется 0.0")
                                0.0
                            }

                            val totalValue = currentPrice * quantity

                            // Прибыль (если есть)
                            var profit = 0.0
                            var profitPercent = 0.0
                            try {
                                val profitObj = it.javaClass.getMethod("getExpectedYield").invoke(it)
                                val units = profitObj.javaClass.getMethod("getUnits").invoke(profitObj) as Long
                                val nano = profitObj.javaClass.getMethod("getNano").invoke(profitObj) as Int
                                profit = units + nano / 1_000_000_000.0
                                if (totalValue > 0) {
                                    profitPercent = (profit / totalValue) * 100
                                }
                            } catch (e: Exception) {
                                // Прибыль не всегда доступна
                            }

                            PortfolioPosition(
                                figi = figi,
                                name = instrument.name,
                                ticker = instrument.ticker,
                                quantity = quantity,
                                currentPrice = currentPrice,
                                totalValue = totalValue,
                                profit = profit,
                                profitPercent = profitPercent
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Ошибка обработки позиции", e)
                            null
                        }
                    }
                }

                val totalValue = positions.sumOf { it.totalValue }

                _uiState.update {
                    it.copy(
                        positions = positions,
                        totalValue = totalValue,
                        isLoading = false,
                        statusMessage = if (positions.isEmpty()) {
                            "Портфель пуст"
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
