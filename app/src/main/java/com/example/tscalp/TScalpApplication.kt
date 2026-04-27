package com.example.tscalp

import android.app.Application
import com.example.tscalp.di.ServiceLocator

class TScalpApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        // Восстанавливаем подключение каждого брокера, если сохранены учётные данные
        val brokerManager = ServiceLocator.getBrokerManager()
        for (brokerName in brokerManager.getAvailableBrokers()) {
            if (ServiceLocator.hasSavedToken(brokerName)) {
                val broker = brokerManager.getBroker(brokerName)
                // Каждый брокер сам знает, как восстановить своё состояние
                // (для Т-Инвестиций это вызов initializeFromSettings, для БКС — initialize из настроек)
                when (brokerName) {
                    "tinkoff" -> (broker as? com.example.tscalp.data.api.TinkoffInvestService)?.initializeFromSettings()
                    "bcs" -> {
                        // Для БКС проверяем, сохранены ли refresh-токен и clientId
                        val creds = ServiceLocator.loadBrokerCredentials("bcs")
                        if (creds != null) {
                            val refreshToken = creds.first
                            val isWriteMode = creds.second
                            val clientId = if (isWriteMode) "trade-api-write" else "trade-api-read"
                            kotlinx.coroutines.runBlocking {
                                (broker as? com.example.tscalp.data.api.BcsBrokerApi)?.initialize(refreshToken, clientId)
                            }
                        }
                    }
                    // mock — ничего не требуется
                }
            }
        }
    }
}