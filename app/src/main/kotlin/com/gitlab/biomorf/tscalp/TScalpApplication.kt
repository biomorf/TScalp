package com.gitlab.biomorf.tscalp

import android.app.Application
import com.gitlab.biomorf.tscalp.data.api.error.TInvestErrorMessages
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.managers.BrokerLifecycleManager
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
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

@HiltAndroidApp
class TScalpApplication : Application() {
    @Inject
    lateinit var brokerManager: BrokerRegistry

    @Inject
    lateinit var lifecycleManager: BrokerLifecycleManager

    @Inject
    lateinit var brokerCredentialsRepository: BrokerCredentialsRepository

    @Inject
    lateinit var logger: Logger

    private val appScope =
        CoroutineScope(
            Dispatchers.IO + SupervisorJob() +
                CoroutineExceptionHandler { _, e ->
                    logger.e("TScalpApplication", "Uncaught error in background scope", e)
                },
        )

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
        if (!brokerCredentialsRepository.hasSavedToken(brokerName)) return

        when (brokerName) {
            BrokerName.TINVEST -> lifecycleManager.restoreTInvest(broker)
            BrokerName.BCS -> lifecycleManager.restoreBcs(broker)
            BrokerName.FINAM -> Unit // Finam инициализируется из UI настроек
        }
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
                    logger.w(
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
                    logger.e(
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
                        logger.e(
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
            logger.w("TScalpApplication", "AppMetrica API key не задан, crash-репортинг отключён")
            return
        }

        val configBuilder = AppMetricaConfig.newConfigBuilder(apiKey)
        if (BuildConfig.DEBUG) {
            configBuilder.withLogs() // включает логирование SDK AppMetrica
        }

        AppMetrica.activate(this, configBuilder.build())
        logger.d("TScalpApplication", "AppMetrica инициализирована (key.len=${apiKey.length})")
    }
}
