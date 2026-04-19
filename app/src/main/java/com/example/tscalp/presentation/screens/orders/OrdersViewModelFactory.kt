package com.example.tscalp.presentation.screens.orders

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tscalp.data.api.TinkoffInvestService

class OrdersViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrdersViewModel::class.java)) {
            val apiService = TinkoffInvestService(context)
            @Suppress("UNCHECKED_CAST")
            return OrdersViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}