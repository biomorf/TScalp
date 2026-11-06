package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Загрузка счетов брокера + выбор счёта по умолчанию.
 *
 * Вынесено из `TInvestSettingsPanel` (clean-audit M006, M2): выбор
 * «если счёт по умолчанию пуст и есть счета — взять первый» был
 * business-правилом в Composable. Теперь — в usecase.
 */
@Singleton
class LoadBrokerAccountsUseCase
    @Inject
    constructor(
        private val accountsRepository: AccountsRepository,
        private val accountPreferencesRepository: AccountPreferencesRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "LoadBrokerAccountsUseCase"
        }

        /**
         * Загружает счета [brokerName] и резолвит счёт по умолчанию.
         *
         * @return `AppResult.Success` с (accounts, defaultAccountId);
         *   `AppResult.Failure` — ошибка загрузки.
         */
        suspend fun execute(
            brokerName: BrokerName,
            sandboxMode: Boolean,
        ): AppResult<LoadAccountsResult> {
            logger.d(TAG, "execute: brokerName=$brokerName, sandboxMode=$sandboxMode")
            return try {
                when (val result = accountsRepository.getAccountsResult(brokerName, sandboxMode)) {
                    is AppResult.Success -> {
                        val accounts = result.data
                        val resolvedDefault =
                            resolveDefaultAccountId(
                                brokerName = brokerName,
                                accounts = accounts,
                            )
                        AppResult.Success(
                            LoadAccountsResult(
                                accounts = accounts,
                                defaultAccountId = resolvedDefault,
                            ),
                        )
                    }
                    is AppResult.Failure -> {
                        logger.e(TAG, "getAccountsResult failed: ${result.error.message}", result.error.cause)
                        result
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "load accounts exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка загрузки счетов: ${e.message}", e))
            }
        }

        private suspend fun resolveDefaultAccountId(
            brokerName: BrokerName,
            accounts: List<BrokerAccount>,
        ): String {
            val saved = accountPreferencesRepository.loadDefaultAccountId(brokerName) ?: ""
            if (saved.isNotBlank()) return saved
            if (accounts.isEmpty()) return ""
            val first = accounts.first().id
            accountPreferencesRepository.saveDefaultAccountId(brokerName, first)
            logger.d(TAG, "resolveDefault: auto-selected first account $first for $brokerName")
            return first
        }
    }

/**
 * Результат [LoadBrokerAccountsUseCase]: счета + резолвленный
 * счёт по умолчанию (существующий или только что выбранный первый).
 */
data class LoadAccountsResult(
    val accounts: List<BrokerAccount>,
    val defaultAccountId: String,
)
