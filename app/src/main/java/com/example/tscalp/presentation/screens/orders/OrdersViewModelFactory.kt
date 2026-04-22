package com.example.tscalp.presentation.screens.orders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tscalp.di.ServiceLocator

/**
 * Фабрика для создания OrdersViewModel с внедрением зависимости TinkoffInvestService.
 * Использует синглтон из ServiceLocator, чтобы все экраны работали с одним экземпляром API.
 */
class OrdersViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrdersViewModel::class.java)) {
            // Получаем глобальный экземпляр сервиса (синглтон)
            val apiService = ServiceLocator.getTinkoffInvestService(context)
            @Suppress("UNCHECKED_CAST")
            return OrdersViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}