package com.example.tscalp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

import com.example.tscalp.data.api.SharedPositionStreamManager
import com.example.tscalp.data.api.BcsBrokerApi
import com.example.tscalp.data.repository.SettingsRepository
import com.example.tscalp.di.BrokerManager

@HiltAndroidApp
class TScalpApplication : Application() {

    @Inject
    lateinit var positionStreamManager: SharedPositionStreamManager

    @Inject
    lateinit var brokerManager: BrokerManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        // Восстанавливаем подключение каждого брокера, если сохранены учётные данные
        for (brokerName in brokerManager.getAvailableBrokers()) {
            if (!settingsRepository.hasSavedToken(brokerName)) continue

            when (brokerName) {
                "TInvest" -> {
                    // DataModule уже инициализировал TInvest через SharedPreferences.
                    // Запускаем общий поток позиций, если известен счёт по умолчанию.
                    val accountId = settingsRepository.loadDefaultAccountId("TInvest")
                    if (accountId != null) {
                        positionStreamManager.start(accountId)
                    }
                }
                "bcs" -> {
                    val creds = settingsRepository.loadBrokerCredentials("bcs")
                    if (creds != null) {
                        val (refreshToken, isWriteMode) = creds
                        val clientId = if (isWriteMode) "trade-api-write" else "trade-api-read"
                        kotlinx.coroutines.runBlocking {
                            (brokerManager.getBroker("bcs") as? BcsBrokerApi)
                                ?.initialize(refreshToken, clientId)
                        }
                    }
                }
            }
        }
    }
}