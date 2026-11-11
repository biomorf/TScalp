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
 * Открытие счёта в песочнице + сохранение его как счёта по умолчанию
 * + перезагрузка списка счетов.
 *
 * Вынесено из `TInvestSettingsPanel` (clean-audit M006, M2): UI
 * оркестрировал open → saveDefault → reload в `scope.launch`.
 *
 * **Reload failure — не fatal.** Если счета не удалось перезагрузить,
 * `OpenResult.accounts` = `null` (счёт открыт, но список устарел).
 * VM оставляет существующий список. Иначе UI показал бы «не удалось
 * открыть», хотя счёт уже создан — поведение изменилось бы.
 *
 * **saveDefaultAccountId failure — не fatal.** Счёт уже открыт; save
 * в DataStore — best-effort (лог `w`), результат операции — успех.
 */
class OpenSandboxAccountUseCase
    constructor(
        private val accountsRepository: AccountsRepository,
        private val accountPreferencesRepository: AccountPreferencesRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "OpenSandboxAccountUseCase"
        }

        /**
         * Открывает новый счёт [brokerName] в песочнице.
         *
         * @return `AppResult.Success` с `accountId` (новый) и `accounts`
         *   (список после reload; `null` — reload failed);
         *   `AppResult.Failure` — ошибка открытия счёта.
         */
        suspend fun execute(
            brokerName: BrokerName,
            sandboxMode: Boolean,
        ): AppResult<OpenResult> {
            logger.d(TAG, "execute: brokerName=$brokerName, sandboxMode=$sandboxMode")
            return try {
                when (val openResult = accountsRepository.openSandboxAccountResult(brokerName)) {
                    is AppResult.Success -> {
                        val newAccountId = openResult.data
                        logger.d(TAG, "opened: newAccountId=$newAccountId")
                        saveDefaultSafely(brokerName, newAccountId)
                        val reloaded = reloadAccounts(brokerName, sandboxMode)
                        AppResult.Success(
                            OpenResult(
                                accountId = newAccountId,
                                accounts = reloaded,
                            ),
                        )
                    }
                    is AppResult.Failure -> {
                        logger.e(TAG, "openSandboxAccount failed: ${openResult.error.message}", openResult.error.cause)
                        openResult
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "openSandboxAccount exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка открытия счёта: ${e.message}", e))
            }
        }

        private suspend fun saveDefaultSafely(
            brokerName: BrokerName,
            accountId: String,
        ) {
            try {
                accountPreferencesRepository.saveDefaultAccountId(brokerName, accountId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(TAG, "saveDefaultAccountId failed (non-fatal): ${e.message}", e)
            }
        }

        private suspend fun reloadAccounts(
            brokerName: BrokerName,
            sandboxMode: Boolean,
        ): List<BrokerAccount>? =
            when (val reload = accountsRepository.getAccountsResult(brokerName, sandboxMode)) {
                is AppResult.Success -> reload.data
                is AppResult.Failure -> {
                    logger.w(TAG, "reload accounts after open failed: ${reload.error.message}", reload.error.cause)
                    null
                }
            }
    }

/**
 * Результат [OpenSandboxAccountUseCase]: новый `accountId` и список
 * счетов после reload.
 *
 * `accounts == null` — reload failed; VM не должен затирать
 * существующий список.
 */
data class OpenResult(
    val accountId: String,
    val accounts: List<BrokerAccount>?,
)
