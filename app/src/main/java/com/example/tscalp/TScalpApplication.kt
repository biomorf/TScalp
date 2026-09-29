package com.example.tscalp

import android.util.Log
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import com.example.tscalp.data.api.SharedPositionStreamManager
import com.example.tscalp.data.api.TInvestBrokerAPI
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
                        val broker = brokerManager.getBroker("TInvest") as? TInvestBrokerAPI
                        if (broker != null) {
                            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                                try {
                                    val sandbox = settingsRepository.isSandboxMode()
                                    val accounts = broker.getAccounts(sandbox)
                                    val savedAccountId = settingsRepository.loadDefaultAccountId("TInvest")

                                    // 1. Сохранённый счёт, если он ещё существует
                                    // 2. Иначе — первый доступный счёт в аккаунте
                                    // 3. Иначе, если песочница — создаём новый
                                    // 4. Иначе (боевой и пусто) — null
                                    val accountId = when {
                                        savedAccountId != null && accounts.any { it.id == savedAccountId } -> savedAccountId
                                        accounts.isNotEmpty() -> accounts.first().id
                                        sandbox -> broker.openSandboxAccount()
                                        else -> null
                                    }

                                    if (accountId != null) {
                                        if (accountId != savedAccountId) {
                                            settingsRepository.saveDefaultAccountId("TInvest", accountId)
                                        }
                                        positionStreamManager.start(accountId)
                                    } else {
                                        Log.w("TScalpApplication", "Не удалось выбрать/создать TInvest-счёт")
                                    }
                                } catch (e: Exception) {
                                    Log.e("TScalpApplication", "Не удалось подготовить TInvest-счёт", e)
                                }
                            }
                        }
                    }

                "bcs" -> {
                    val creds = settingsRepository.loadBrokerCredentials("bcs")
                    if (creds != null) {
                        val (refreshToken, isWriteMode) = creds
                        val clientId = if (isWriteMode) "trade-api-write" else "trade-api-read"
                        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                            try {
                                (brokerManager.getBroker("bcs") as? BcsBrokerApi)
                                    ?.initialize(refreshToken, clientId)
                            } catch (e: Exception) {
                                Log.e("TScalpApplication", "Не удалось инициализировать BCS", e)
                            }
                        }
                    }
                }
            }
        }
    }
}