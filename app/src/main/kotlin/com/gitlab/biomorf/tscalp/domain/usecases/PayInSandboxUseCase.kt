package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.SandboxMoney
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Пополнение счёта в песочнице.
 *
 * Вынесено из `PortfolioViewModel.payInSandbox` (clean-audit M003):
 * VM больше не оркестрирует вызов репозитория и разбор `AppResult` —
 * только делегирует и обновляет `UiState` (presentation).
 */
@Singleton
class PayInSandboxUseCase
    @Inject
    constructor(
        private val accountsRepository: AccountsRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "PayInSandboxUseCase"
        }

        /**
         * Пополняет счёт [accountId] на [amount].
         *
         * @return `AppResult.Success(Unit)` — успех;
         *   `AppResult.Failure` — ошибка (в `message` уже готовая
         *   строка для `StatusEvent.message`).
         */
        suspend fun execute(
            accountId: String,
            amount: SandboxMoney,
        ): AppResult<Unit> {
            logger.d(TAG, "execute: accountId=$accountId, amount=${amount.units} ${amount.currency}")
            return try {
                when (val result = accountsRepository.sandboxPayInResult(accountId, amount)) {
                    is AppResult.Success -> {
                        logger.d(TAG, "sandboxPayIn: success")
                        AppResult.Success(Unit)
                    }
                    is AppResult.Failure -> {
                        logger.e(TAG, "sandboxPayIn failed: ${result.error.message}", result.error.cause)
                        result
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "sandboxPayIn exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка пополнения: ${e.message}", e))
            }
        }
    }
