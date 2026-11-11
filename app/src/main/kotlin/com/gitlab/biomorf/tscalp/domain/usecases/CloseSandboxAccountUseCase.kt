package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CancellationException

/**
 * Закрытие счёта в песочнице + сброс счёта по умолчанию
 * + перезагрузка списка счетов.
 *
 * Вынесено из `TInvestSettingsPanel` (clean-audit M006, M2): UI
 * оркестрировал close → saveDefault("") → reload в `scope.launch`.
 *
 * **Reload failure — не fatal.** Если счета не удалось перезагрузить,
 * `CloseResult.accounts` = `null` (счёт закрыт, но список устарел).
 * VM оставляет существующий список. Иначе UI показал бы «не удалось
 * закрыть», хотя счёт уже закрыт.
 *
 * **saveDefaultAccountId("") failure — best-effort.** Счёт уже закрыт;
 * если сброс дефолта в DataStore не удался, дефолт остаётся указывать
 * на закрытый счёт — это UI-проблема, не операция. Лог `w`.
 */
class CloseSandboxAccountUseCase
    constructor(
        private val accountsRepository: AccountsRepository,
        private val accountPreferencesRepository: AccountPreferencesRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "CloseSandboxAccountUseCase"
        }

        /**
         * Закрывает счёт [accountId] брокера [brokerName].
         *
         * @return `AppResult.Success` с `accounts` после reload
         *   (`null` — reload failed); `AppResult.Failure` — ошибка
         *   закрытия счёта.
         */
        suspend fun execute(
            brokerName: BrokerName,
            sandboxMode: Boolean,
            accountId: String,
        ): AppResult<CloseResult> {
            logger.d(TAG, "execute: brokerName=$brokerName, sandboxMode=$sandboxMode, accountId=$accountId")
            return try {
                when (val closeResult = accountsRepository.closeSandboxAccountResult(brokerName, accountId)) {
                    is AppResult.Success -> {
                        logger.d(TAG, "closed: accountId=$accountId")
                        resetDefaultSafely(brokerName)
                        val reloaded = reloadAccounts(brokerName, sandboxMode)
                        AppResult.Success(CloseResult(accounts = reloaded))
                    }
                    is AppResult.Failure -> {
                        logger.e(TAG, "closeSandboxAccount failed: ${closeResult.error.message}", closeResult.error.cause)
                        closeResult
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "closeSandboxAccount exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка закрытия счёта: ${e.message}", e))
            }
        }

        private suspend fun resetDefaultSafely(brokerName: BrokerName) {
            try {
                accountPreferencesRepository.saveDefaultAccountId(brokerName, "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "reset default account failed (non-fatal): ${e.message}", e)
            }
        }

        private suspend fun reloadAccounts(
            brokerName: BrokerName,
            sandboxMode: Boolean,
        ): List<BrokerAccount>? =
            when (val reload = accountsRepository.getAccountsResult(brokerName, sandboxMode)) {
                is AppResult.Success -> reload.data
                is AppResult.Failure -> {
                    logger.w(TAG, "reload accounts after close failed: ${reload.error.message}", reload.error.cause)
                    null
                }
            }
    }

/**
 * Результат [CloseSandboxAccountUseCase]: список счетов после reload.
 *
 * `accounts == null` — reload failed; VM не должен затирать
 * существующий список.
 */
data class CloseResult(
    val accounts: List<BrokerAccount>?,
)
