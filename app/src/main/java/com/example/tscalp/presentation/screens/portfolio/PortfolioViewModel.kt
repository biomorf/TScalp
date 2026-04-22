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
import java.math.RoundingMode

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
                    when {
                        // Песочница: PortfolioResponse.getPositionsList()
                        portfolio.javaClass.methods.any { it.name == "getPositionsList" } -> {
                            portfolio.javaClass.getMethod("getPositionsList").invoke(portfolio) as List<*>
                        }
                        // Боевой режим: Portfolio.getPositions()
                        portfolio.javaClass.methods.any { it.name == "getPositions" } -> {
                            portfolio.javaClass.getMethod("getPositions").invoke(portfolio) as List<*>
                        }
                        // Fallback на поле positions
                        else -> {
                            val field = portfolio.javaClass.getDeclaredField("positions")
                            field.isAccessible = true
                            field.get(portfolio) as List<*>
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Не удалось получить список позиций", e)
                    emptyList<Any?>()
                }

                Log.d(TAG, "Получено позиций: ${positionsList.size}")

                val positions = positionsList.mapNotNull { position ->
                    position?.let {
                        try {
                            // FIGI
                            val figi = it.javaClass.getMethod("getFigi").invoke(it) as String
                            val instrument = apiService.getInstrumentByFigi(figi)

                            // Количество (BigDecimal или Quotation)
                            val quantity: Long = try {
                                val qtyObj = it.javaClass.getMethod("getQuantity").invoke(it)
                                when (qtyObj) {
                                    is BigDecimal -> qtyObj.toLong()
                                    else -> {
                                        // Quotation: пробуем units/nano
                                        try {
                                            val units = qtyObj.javaClass.getMethod("getUnits").invoke(qtyObj) as Long
                                            val nano = qtyObj.javaClass.getMethod("getNano").invoke(qtyObj) as Int
                                            units + nano / 1_000_000_000
                                        } catch (e: Exception) {
                                            // Fallback: toBigDecimal()
                                            val bd = qtyObj.javaClass.getMethod("toBigDecimal").invoke(qtyObj) as? BigDecimal
                                            bd?.toLong() ?: 0L
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Не удалось извлечь количество для $figi: ${e.message}")
                                0L
                            }

                            if (quantity == 0L) return@mapNotNull null

                            // Текущая цена (Money -> Double)
                            val currentPrice: Double = try {
                                val priceObj = it.javaClass.getMethod("getCurrentPrice").invoke(it)
                                // Пробуем toBigDecimal (основной способ для Money)
                                try {
                                    val bd = priceObj.javaClass.getMethod("toBigDecimal").invoke(priceObj) as? BigDecimal
                                    bd?.toDouble() ?: 0.0
                                } catch (e: Exception) {
                                    // Fallback на units/nano
                                    val units = priceObj.javaClass.getMethod("getUnits").invoke(priceObj) as Long
                                    val nano = priceObj.javaClass.getMethod("getNano").invoke(priceObj) as Int
                                    units + nano / 1_000_000_000.0
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Не удалось извлечь цену для $figi: ${e.message}")
                                0.0
                            }

                            val totalValue = currentPrice * quantity

                            // Прибыль
                            var profit = 0.0
                            var profitPercent = 0.0
                            try {
                                val yieldObj = it.javaClass.getMethod("getExpectedYield").invoke(it)
                                val bd = when (yieldObj) {
                                    is BigDecimal -> yieldObj
                                    else -> yieldObj.javaClass.getMethod("toBigDecimal").invoke(yieldObj) as? BigDecimal
                                }
                                profit = bd?.toDouble() ?: 0.0
                                if (totalValue > 0) {
                                    profitPercent = (profit / totalValue) * 100
                                }
                            } catch (e: Exception) {
                                // не критично
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
                        statusMessage = if (positions.isEmpty()) "Портфель пуст" else "Загружено ${positions.size} позиций",
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
