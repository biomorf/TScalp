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
 * Загрузка счетов для экрана «Заявки» + выбор счёта по умолчанию
 * (saved **валидируется** по списку accounts, fallback — первый,
 * **без сохранения** в DataStore).
 *
 * Вынесено из `OrdersViewModel.loadAccounts` (clean-audit M013, M2):
 * getAccounts + выбор счёта — domain-orchestration, не presentation.
 *
 * **Не путать с [LoadBrokerAccountsUseCase]** (M006, settings-panel).
 * Семантика намеренно разная (Q8, 2026-11-07):
 * - [LoadBrokerAccountsUseCase]: saved **не** валидируется по списку;
 *   при пустом saved + непустом accounts — **сохраняет** first в
 *   DataStore. Контракт settings-panel: «default-счёт в настройках».
 * - этот usecase: saved **валидируется** по списку (stale saved →
 *   fallback на first); first **не** сохраняется. Контракт
 *   Orders: «счёт для формы заявки», default-счёт ставится через
 *   диалог карточки.
 *
 * Объединение в один usecase дало бы две регрессии:
 * - stale saved (счёт удалён, но остался в DataStore) → selected
 *   для несуществующего счёта;
 * - побочный `saveDefaultAccountId(first)` — поведение, которого
 *   у `OrdersViewModel.loadAccounts` не было.
 *
 * Раздельные usecase — осознанное решение (2d), не дублирование.
 *
 * VM-часть (Q2 + F3) остаётся в VM: `startPositionUpdates()` при
 * непустом selectedAccountId.
 */
@Singleton
class LoadOrdersAccountsUseCase
    @Inject
    constructor(
        private val accountsRepository: AccountsRepository,
        private val accountPreferencesRepository: AccountPreferencesRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "LoadOrdersAccountsUseCase"
        }

        /**
         * Загружает счета [brokerName] и резолвит выбранный счёт
         * (saved-if-in-list / first / `null`).
         *
         * @return `AppResult.Success(OrdersAccountsResult)` — accounts
         *   + выбранный счёт (`null`, если accounts пуст);
         *   `AppResult.Failure` — ошибка getAccounts / общая ошибка.
         */
        suspend fun execute(
            brokerName: BrokerName,
            sandboxMode: Boolean,
        ): AppResult<OrdersAccountsResult> {
            logger.d(TAG, "execute: brokerName=$brokerName, sandboxMode=$sandboxMode")
            return try {
                val accountsResult = accountsRepository.getAccountsResult(brokerName, sandboxMode)
                when (accountsResult) {
                    is AppResult.Success -> {
                        val accounts = accountsResult.data
                        val savedAccountId = accountPreferencesRepository.loadDefaultAccountId(brokerName)
                        val chosenAccount =
                            accounts.firstOrNull { it.id == savedAccountId }
                                ?: accounts.firstOrNull()
                        AppResult.Success(
                            OrdersAccountsResult(
                                accounts = accounts,
                                selectedAccountId = chosenAccount?.id,
                            ),
                        )
                    }
                    is AppResult.Failure -> {
                        logger.e(
                            TAG,
                            "getAccountsResult failed: ${accountsResult.error.message}",
                            accountsResult.error.cause,
                        )
                        accountsResult
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "load orders accounts exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка загрузки счетов: ${e.message}", e))
            }
        }
    }

/**
 * Результат [LoadOrdersAccountsUseCase]: счета + выбранный счёт.
 * `selectedAccountId = null` только при пустом списке accounts.
 */
data class OrdersAccountsResult(
    val accounts: List<BrokerAccount>,
    val selectedAccountId: String?,
)
