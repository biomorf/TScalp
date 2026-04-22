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
import java.math.BigDecimal

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
                            // 1. FIGI
                            val figi = it.javaClass.getMethod("getFigi").invoke(it) as String
                            val instrument = apiService.getInstrumentByFigi(figi)

                            // 2. Количество (BigDecimal -> Long)
                            val quantity: Long = try {
                                val qtyMethod = it.javaClass.getMethod("getQuantity")
                                val qtyValue = qtyMethod.invoke(it) as? BigDecimal
                                qtyValue?.toLong() ?: 0L
                            } catch (e: Exception) {
                                Log.w(TAG, "Не удалось извлечь количество для $figi: ${e.message}")
                                0L
                            }

                            if (quantity == 0L) return@mapNotNull null

                            // 3. Текущая цена (Money -> Double)
                            val currentPrice: Double = try {
                                val priceMethod = it.javaClass.getMethod("getCurrentPrice")
                                val moneyObj = priceMethod.invoke(it)
                                val units = moneyObj.javaClass.getMethod("getUnits").invoke(moneyObj) as Long
                                val nano = moneyObj.javaClass.getMethod("getNano").invoke(moneyObj) as Int
                                units + nano / 1_000_000_000.0
                            } catch (e: Exception) {
                                Log.w(TAG, "Не удалось извлечь цену для $figi: ${e.message}")
                                0.0
                            }

                            val totalValue = currentPrice * quantity

                            // 4. Прибыль (BigDecimal -> Double)
                            var profit = 0.0
                            var profitPercent = 0.0
                            try {
                                val yieldMethod = it.javaClass.getMethod("getExpectedYield")
                                val yieldValue = yieldMethod.invoke(it) as? BigDecimal
                                profit = yieldValue?.toDouble() ?: 0.0
                                if (totalValue > 0) {
                                    profitPercent = (profit / totalValue) * 100
                                }
                            } catch (e: Exception) {
                                // прибыль может отсутствовать
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
