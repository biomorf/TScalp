package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Подключение к брокеру: сохранение credentials + инициализация через
 * lifecycle-роль + обновление [BrokerRegistry.anyInitialized].
 *
 * Вынесено из `SettingsViewModel.initialize*` (clean-audit M004) и
 * вызывается из VM (`connect*`, M007) — presentation не оркестрирует
 * login-сценарий.
 *
 * Порядок `save` / `initialize` — **1:1 как в старом VM**:
 * - TInvest: save → initialize (токен сохраняется до попытки).
 * - Bcs: initialize → save (битый refresh-токен не сохраняется).
 * - Finam: save → initialize.
 *
 * **Без `withContext(Dispatchers.IO)`**: `BrokerCredentialsRepository`
 * и `LifecycleApi.initialize` — suspend / dispatch-safe. Если live-test
 * покажет ANR — отдельная задача (clean-audit M005).
 */
@Singleton
class InitializeBrokerUseCase
    @Inject
    constructor(
        private val brokerRegistry: BrokerRegistry,
        private val credentialsRepository: BrokerCredentialsRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "InitializeBrokerUseCase"
        }

        /**
         * Подключает брокера по [credentials].
         *
         * @return `AppResult.Success(true)` — успех;
         *   `AppResult.Failure` — ошибка (в `message` уже готовая строка
         *   для `StatusEvent.message`).
         */
        suspend fun execute(credentials: BrokerCredentials): AppResult<Boolean> {
            logger.d(TAG, "execute: ${credentials::class.simpleName}")
            return try {
                val initialized =
                    when (credentials) {
                        is BrokerCredentials.TInvest -> initializeTInvest(credentials)
                        is BrokerCredentials.Bcs -> initializeBcs(credentials)
                        is BrokerCredentials.Finam -> initializeFinam(credentials)
                    }
                if (!initialized) {
                    logger.w(TAG, "initialize returned false for ${credentials::class.simpleName}")
                    return AppResult.Failure(AppError.Unknown("Инициализация брокера не удалась"))
                }
                brokerRegistry.refreshInitializationState()
                AppResult.Success(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "initialize failed", e)
                AppResult.Failure(AppError.Unknown("Ошибка подключения: ${e.message}", e))
            }
        }

        private suspend fun initializeTInvest(creds: BrokerCredentials.TInvest): Boolean {
            val cleanToken = creds.token.trim()
            credentialsRepository.saveBrokerCredentials(
                brokerName = BrokerName.TINVEST,
                token = cleanToken,
                sandbox = creds.sandbox,
            )
            return brokerRegistry
                .getBroker(BrokerName.TINVEST)
                ?.lifecycleTInvest
                ?.initialize(BrokerCredentials.TInvest(cleanToken, creds.sandbox)) ?: false
        }

        private suspend fun initializeBcs(creds: BrokerCredentials.Bcs): Boolean {
            val cleanToken = creds.refreshToken.trim()
            val initialized =
                brokerRegistry
                    .getBroker(BrokerName.BCS)
                    ?.lifecycleBcs
                    ?.initialize(BrokerCredentials.Bcs(cleanToken, creds.isWriteMode)) ?: false
            if (initialized) {
                credentialsRepository.saveBrokerCredentials(
                    brokerName = BrokerName.BCS,
                    token = cleanToken,
                    sandbox = creds.isWriteMode,
                )
            }
            return initialized
        }

        private suspend fun initializeFinam(creds: BrokerCredentials.Finam): Boolean {
            val cleanToken = creds.token.trim()
            credentialsRepository.saveToken(BrokerName.FINAM, cleanToken)
            return brokerRegistry
                .getBroker(BrokerName.FINAM)
                ?.lifecycleFinam
                ?.initialize(BrokerCredentials.Finam(cleanToken)) ?: false
        }
    }
