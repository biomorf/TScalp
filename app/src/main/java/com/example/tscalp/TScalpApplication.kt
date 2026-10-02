package com.example.tscalp

import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler

import com.example.tscalp.data.api.SharedPositionStreamManager
import com.example.tscalp.data.api.TInvestBrokerAPI
import com.example.tscalp.data.api.BcsBrokerApi
import com.example.tscalp.data.repository.SettingsRepository
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.BrokerName
import com.example.tscalp.util.AppLogger

private val appScope = CoroutineScope(
    Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
        AppLogger.e("TScalpApplication", "Uncaught error in background scope", e)
    }
)

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

        initAppMetrica()

        // Восстанавливаем подключение каждого брокера, если сохранены учётные данные
        for (brokerName in BrokerName.entries) {
            val broker = brokerManager.getBroker(brokerName) ?: continue
            if (!settingsRepository.hasSavedToken(brokerName)) continue

            when (brokerName) {
                BrokerName.TINVEST -> {
                    // DataModule уже инициализировал TInvest через SharedPreferences.
                    // Запускаем общий поток позиций, если известен счёт по умолчанию.
                    val tInvest = broker as? TInvestBrokerAPI ?: continue
                    appScope.launch {
                        try {
                            val sandbox = settingsRepository.isSandboxMode()
                            val accounts = tInvest.getAccounts(sandbox)
                            val savedAccountId = settingsRepository.loadDefaultAccountId(brokerName)

                            // 1. Сохранённый счёт, если он ещё существует
                            // 2. Иначе — первый доступный счёт в аккаунте
                            // 3. Иначе, если песочница — создаём новый
                            // 4. Иначе (боевой и пусто) — null
                            val accountId = when {
                                savedAccountId != null && accounts.any { it.id == savedAccountId } -> savedAccountId
                                accounts.isNotEmpty() -> accounts.first().id
                                sandbox -> tInvest.openSandboxAccount()
                                else -> null
                            }

                            if (accountId != null) {
                                if (accountId != savedAccountId) {
                                    settingsRepository.saveDefaultAccountId(brokerName, accountId)
                                }
                                positionStreamManager.start(accountId)
                            } else {
                                AppLogger.w("TScalpApplication", "Не удалось выбрать/создать TInvest-счёт")
                            }
                        } catch (e: Exception) {
                            AppLogger.e("TScalpApplication", "Не удалось подготовить TInvest-счёт", e)
                        }
                    }
                }

                BrokerName.BCS -> {
                    val creds = settingsRepository.loadBrokerCredentials(brokerName)
                    if (creds != null) {
                        val (refreshToken, isWriteMode) = creds
                        val clientId = if (isWriteMode) "trade-api-write" else "trade-api-read"
                        appScope.launch {
                            try {
                                (broker as? BcsBrokerApi)?.initialize(refreshToken, clientId)
                            } catch (e: Exception) {
                                AppLogger.e("TScalpApplication", "Не удалось инициализировать BCS", e)
                            }
                        }
                    }
                }

                BrokerName.FINAM -> {
                    // Finam инициализируется из UI настроек, при старте приложения ничего не делаем
                }
            }
        }
    }

    private fun initAppMetrica() {
        val apiKey = BuildConfig.APPMETRICA_API_KEY
        if (apiKey.isBlank()) {
            AppLogger.w("TScalpApplication", "AppMetrica API key не задан, crash-репортинг отключён")
            return
        }

        val configBuilder = AppMetricaConfig.newConfigBuilder(apiKey)
        if (BuildConfig.DEBUG) {
            configBuilder.withLogs()   // включает логирование SDK AppMetrica
        }

        AppMetrica.activate(this, configBuilder.build())
        AppLogger.d("TScalpApplication", "AppMetrica инициализирована (key.len=${apiKey.length})")
    }
}