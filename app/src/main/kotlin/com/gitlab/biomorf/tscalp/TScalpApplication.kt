package com.gitlab.biomorf.tscalp

import android.app.Application
import com.gitlab.biomorf.tscalp.data.repository.SettingsRepository
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.api.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.util.AppLogger
import com.gitlab.biomorf.tscalp.util.TInvestErrorMessages
import dagger.hilt.android.HiltAndroidApp
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.CancellationException
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
    lateinit var positionFlowManager: PositionFlowManager

    @Inject
    lateinit var brokerManager: BrokerManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        initAppMetrica()
        installGrpcExceptionGuard()

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
     * Инициализирует TInvest через lifecycle и запускает стрим позиций.
     *
     * Счёт выбирается через [resolveTInvestAccountId]: при успешном
     * `getAccounts` используется сохранённый или первый доступный счёт,
     * при падении — сохранённый (если есть), иначе в песочнице
     * открывается новый.
     *
     * `positionFlowManager.start()` вызывается независимо от того,
     * удалось ли получить список счетов — это позволяет стримам
     * работать при временном сбое `getAccounts`.
     */
    private suspend fun restoreTInvest(broker: BrokerApi) {
        val creds = settingsRepository.loadBrokerCredentials(BrokerName.TINVEST)
        if (creds == null) {
            AppLogger.w("TScalpApplication", "TInvest: нет сохранённых креденшелов")
            return
        }
        val (token, sandbox) = creds

        val initialized =
            broker.lifecycle?.initialize(BrokerCredentials.TInvest(token, sandbox)) ?: false
        if (!initialized) {
            AppLogger.e("TScalpApplication", "TInvest: не удалось инициализировать API")
            return
        }
        brokerManager.refreshInitializationState()

        val savedAccountId = settingsRepository.loadDefaultAccountId(BrokerName.TINVEST)
        val accountId = resolveTInvestAccountId(broker, sandbox, savedAccountId)

        if (accountId == null) {
            AppLogger.w("TScalpApplication", "TInvest: не удалось выбрать/создать счёт")
            return
        }

        if (accountId != savedAccountId) {
            settingsRepository.saveDefaultAccountId(BrokerName.TINVEST, accountId)
        }
        positionFlowManager.start(accountId)
    }

    /**
     * Выбирает accountId для T-Invest с fallback на сохранённый счёт.
     *
     * Порядок:
     * 1. `getAccounts` успешно:
     *    - savedAccountId есть в списке → он;
     *    - иначе список не пуст → первый;
     *    - иначе sandbox → новый счёт в песочнице;
     *    - иначе null.
     * 2. `getAccounts` упало:
     *    - savedAccountId есть → он (главный fallback);
     *    - иначе sandbox → новый счёт в песочнице;
     *    - иначе null.
     */
    private suspend fun resolveTInvestAccountId(
        broker: BrokerApi,
        sandbox: Boolean,
        savedAccountId: String?,
    ): String? {
        val accounts =
            try {
                broker.getAccounts(sandbox)
            } catch (e: Exception) {
                AppLogger.w("TScalpApplication", "TInvest: getAccounts failed, fallback to savedAccountId", e)
                null
            }

        return when {
            accounts == null -> savedAccountId ?: tryOpenSandboxAccount(broker, sandbox)
            savedAccountId != null && accounts.any { it.id == savedAccountId } -> savedAccountId
            accounts.isNotEmpty() -> accounts.first().id
            sandbox -> tryOpenSandboxAccount(broker, sandbox)
            else -> null
        }
    }

    /**
     * Открывает новый счёт в песочнице. Null вне sandbox или при ошибке.
     */
    private suspend fun tryOpenSandboxAccount(
        broker: BrokerApi,
        sandbox: Boolean,
    ): String? {
        if (!sandbox) return null
        return try {
            broker.openSandboxAccount()
        } catch (e: Exception) {
            AppLogger.w("TScalpApplication", "TInvest: openSandboxAccount failed", e)
            null
        }
    }

    /**
     * Инициализирует BCS через lifecycle по сохранённым refresh-токену и режиму записи.
     */
    private suspend fun restoreBcs(broker: BrokerApi) {
        val creds = settingsRepository.loadBrokerCredentials(BrokerName.BCS) ?: return
        val (refreshToken, isWriteMode) = creds
        val initialized =
            broker.lifecycle?.initialize(BrokerCredentials.Bcs(refreshToken, isWriteMode)) ?: false
        if (!initialized) {
            AppLogger.e("TScalpApplication", "Не удалось инициализировать BCS")
            return
        }
        brokerManager.refreshInitializationState()
    }

    /**
     * Устанавливает глобальный [Thread.UncaughtExceptionHandler], который
     * перехватывает `io.grpc.StatusException` / `StatusRuntimeException`,
     * доставленные в уже завершённую корутину `ClientCalls`, и не даёт им
     * убить процесс. Остальные исключения пробрасываются в оригинальный
     * handler (в проде это handler AppMetrica, поставленный SDK при
     * `activate`). `CancellationException` не считается ошибкой и не
     * репортится.
     *
     * Если оригинальный handler отсутствует (теоретически возможно, на
     * Android не должно случаться — `RuntimeInit` ставит
     * `KillApplicationHandler` до `Application.onCreate`), пробрасываем
     * исключение вверх: лучше упасть, чем молча проглотить фатальный краш.
     */
    private fun installGrpcExceptionGuard() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            when {
                throwable is CancellationException -> {
                    AppLogger.w(
                        "TScalpApplication",
                        "Uncaught CancellationException (ignored)",
                        throwable,
                    )
                }

                throwable is StatusException || throwable is StatusRuntimeException -> {
                    val status = Status.fromThrowable(throwable)
                    val description = status.description
                    val humanMessage =
                        description?.let { TInvestErrorMessages.messages[it] }
                            ?: "gRPC ${status.code}"
                    AppLogger.e(
                        "TScalpApplication",
                        "Unhandled gRPC ${status.code}: $humanMessage (description=$description)",
                        throwable,
                    )
                    AppMetrica.reportError("Unhandled gRPC", throwable)
                }

                else -> {
                    if (original != null) {
                        original.uncaughtException(thread, throwable)
                    } else {
                        AppLogger.e(
                            "TScalpApplication",
                            "No original UncaughtExceptionHandler; rethrowing to kill process",
                            throwable,
                        )
                        throw throwable
                    }
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
            configBuilder.withLogs() // включает логирование SDK AppMetrica
        }

        AppMetrica.activate(this, configBuilder.build())
        AppLogger.d("TScalpApplication", "AppMetrica инициализирована (key.len=${apiKey.length})")
    }
}
