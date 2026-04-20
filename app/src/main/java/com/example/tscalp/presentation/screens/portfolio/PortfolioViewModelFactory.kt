package com.example.tscalp.presentation.screens.portfolio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tscalp.di.ServiceLocator

class PortfolioViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PortfolioViewModel::class.java)) {
            val apiService = ServiceLocator.getTinkoffInvestService(context)
            @Suppress("UNCHECKED_CAST")
            return PortfolioViewModel(apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}