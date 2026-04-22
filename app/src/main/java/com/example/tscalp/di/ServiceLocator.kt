package com.example.tscalp.di

import android.content.Context
import com.example.tscalp.data.api.TinkoffInvestService

object ServiceLocator {

    @Volatile
    private var apiService: TinkoffInvestService? = null

    fun getTinkoffInvestService(context: Context): TinkoffInvestService {
        return apiService ?: synchronized(this) {
//            apiService ?: TinkoffInvestService(context.applicationContext).also {
//                //apiService = it
//                it.tryRestoreConnection()  // ← автоматическое восстановление
//                apiService = it
//            }

            // same more explicit
            apiService ?: run {
                val service = TinkoffInvestService(context.applicationContext)
                service.tryRestoreConnection()
                apiService = service
                service
            }
        }
    }
}