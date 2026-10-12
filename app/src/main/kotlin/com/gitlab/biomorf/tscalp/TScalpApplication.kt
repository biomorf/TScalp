package com.gitlab.biomorf.tscalp

import android.app.Application
import com.gitlab.biomorf.tscalp.data.api.BcsBrokerApi
import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.data.repository.SettingsRepository
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.PositionStreamManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private val appScope =
    CoroutineScope(
        Dispatchers.IO + SupervisorJob() +
            CoroutineExceptionHandler { _, e ->
                AppLogger.e("TScalpApplication", "Uncaught error in background scope", e)
            },
    )

@HiltAndroidApp
class TScalpApplication : Application() {
    @Inject
    lateinit var positionStreamManager: PositionStreamManager

    @Inject
    lateinit var brokerManager: BrokerManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        initAppMetrica()

        // Восстанавливаем подключение каждого брокера, если сохранены учётные данные.
        // SettingsRepository работает на DataStore — все чтения suspend.
        appScope.launch {
            for (brokerName in BrokerName.entries) {
                restoreBrokerConnection(brokerName)
            }
        }
    }

    /**
     * Восстанавливает подключение одного брокера, если есть сохранённый токен.
     * Диспатчит на конкретный метод по имени брокера.
     */
    private suspend fun restoreBrokerConnection(brokerName: BrokerName) {
        val broker = brokerManager.getBroker(brokerName) ?: return
        if (!settingsRepository.hasSavedToken(brokerName)) return

        when (brokerName) {
            BrokerName.TINVEST -> restoreTInvest(broker)
            BrokerName.BCS -> restoreBcs(broker)
            BrokerName.FINAM -> Unit // Finam инициализируется из UI настроек
        }
    }

    /**
     * Инициализирует TInvestBrokerAPI и запускает стрим позиций
     * по сохранённому счёту (или создаёт новый в песочнице).
     *
     * Выбор счёта:
     * 1. Сохранённый счёт, если он ещё существует.
     * 2. Иначе — первый доступный счёт в аккаунте.
     * 3. Иначе, если песочница — создаём новый.
     * 4. Иначе (боевой и пусто) — ничего не делаем.
     */
    private suspend fun restoreTInvest(broker: BrokerApi) {
        val tInvest = broker as? TInvestBrokerAPI ?: return
        try {
            val creds = settingsRepository.loadBrokerCredentials(BrokerName.TINVEST)
            if (creds == null) {
                AppLogger.w("TScalpApplication", "TInvest: нет сохранённых креденшелов")
                return
            }
            val (token, sandbox) = creds
            tInvest.initialize(token, sandbox)
            brokerManager.refreshInitializationState()

            val accounts = tInvest.getAccounts(sandbox)
            val savedAccountId = settingsRepository.loadDefaultAccountId(BrokerName.TINVEST)

            val accountId =
                when {
                    savedAccountId != null && accounts.any { it.id == savedAccountId } -> savedAccountId
                    accounts.isNotEmpty() -> accounts.first().id
                    sandbox -> tInvest.openSandboxAccount()
                    else -> null
                }

            if (accountId != null) {
                if (accountId != savedAccountId) {
                    settingsRepository.saveDefaultAccountId(BrokerName.TINVEST, accountId)
                }
                positionStreamManager.start(accountId)
            } else {
                AppLogger.w("TScalpApplication", "Не удалось выбрать/создать TInvest-счёт")
            }
        } catch (e: Exception) {
            AppLogger.e("TScalpApplication", "Не удалось подготовить TInvest-счёт", e)
        }
    }

    /**
     * Инициализирует BcsBrokerApi по сохранённым refresh-токену и режиму записи.
     */
    private suspend fun restoreBcs(broker: BrokerApi) {
        val creds = settingsRepository.loadBrokerCredentials(BrokerName.BCS) ?: return
        val (refreshToken, isWriteMode) = creds
        val clientId = if (isWriteMode) "trade-api-write" else "trade-api-read"
        try {
            (broker as? BcsBrokerApi)?.initialize(refreshToken, clientId)
            brokerManager.refreshInitializationState()
        } catch (e: Exception) {
            AppLogger.e("TScalpApplication", "Не удалось инициализировать BCS", e)
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
            configBuilder.withLogs() // включает логирование SDK AppMetrica
        }

        AppMetrica.activate(this, configBuilder.build())
        AppLogger.d("TScalpApplication", "AppMetrica инициализирована (key.len=${apiKey.length})")
    }
}
