package com.example.tscalp.presentation.screens.portfolio

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

/**
 * ViewModel для экрана портфеля.
 */
class PortfolioViewModel(
    private val repository: InvestRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PortfolioViewModel"
    }

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    init {
        checkApiInitialization()
    }

    private fun checkApiInitialization() {
        val service = TinkoffInvestService()
        _uiState.update {
            it.copy(isApiInitialized = service.isInitialized)
        }
        if (service.isInitialized) {
            loadPortfolio()
        }
    }

    fun loadPortfolio() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            try {
                // TODO: получать sandboxMode из настроек
                val sandboxMode = false
                val accounts = repository.getAccounts(sandboxMode)
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
                val positions = repository.getPortfolio(accountId, sandboxMode)
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
        loadPortfolio()
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, isError = false) }
    }
}

/**
 * Фабрика для создания PortfolioViewModel.
 */
class PortfolioViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PortfolioViewModel::class.java)) {
            val service = TinkoffInvestService()
            val repository = InvestRepository(service)
            @Suppress("UNCHECKED_CAST")
            return PortfolioViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}