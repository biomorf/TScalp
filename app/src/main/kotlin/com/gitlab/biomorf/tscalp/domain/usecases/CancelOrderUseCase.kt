package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.OrdersRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Order
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отмена заявки: получить счета → первый счёт → dispatch по типу
 * (Stop / Regular) → отмена через [OrdersRepository].
 *
 * Вынесено из `OrdersListViewModel.cancelOrder` (clean-audit M013, M2):
 * getAccounts + выбор счёта + dispatch stop/regular — domain-
 * orchestration, не presentation.
 *
 * VM-часть (Q2 + F3) остаётся в VM: `loadOrders()` при успехе и
 * `_uiState.update(statusMessage, isError)` при ошибке.
 *
 * **Пустой accounts — не тихий return (Q9, 2026-11-07).** Раньше
 * `return@launch` без сообщения: пользователь нажимал «Отменить»,
 * ничего не происходило. Usecase возвращает `AppResult.Failure`
 * с понятным текстом, VM его показывает.
 *
 * **Pre-existing асимметрия:** `cancelStopOrderResult` в
 * [OrdersRepository] принимает `(accountId, orderId)` **без**
 * `brokerName`, в отличие от `cancelOrderResult`. Не в scope M013;
 * выравнивание — при `### Реализация BCS/Finam Broker API`
 * (мультиброкерный рефакторинг `OrdersRepository`).
 */
@Singleton
class CancelOrderUseCase
    @Inject
    constructor(
        private val accountsRepository: AccountsRepository,
        private val ordersRepository: OrdersRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "CancelOrderUseCase"
            private const val NO_ACCOUNTS = "Нет доступных счетов"
        }

        /**
         * Отменяет [order] на счёте [brokerName] (берётся первый
         * доступный счёт).
         *
         * @return `AppResult.Success(Unit)` — заявка отменена;
         *   `AppResult.Failure` — ошибка (в `message` готовая строка
         *   для `statusMessage`), включая пустой список счетов.
         */
        suspend fun execute(
            brokerName: BrokerName,
            order: Order,
            sandboxMode: Boolean,
        ): AppResult<Unit> {
            logger.d(
                TAG,
                "execute: brokerName=$brokerName, orderId=${order.orderId}, isStop=${order is Order.Stop}",
            )
            return try {
                val accountsResult = accountsRepository.getAccountsResult(brokerName, sandboxMode)
                when (accountsResult) {
                    is AppResult.Success -> {
                        val accounts = accountsResult.data
                        if (accounts.isEmpty()) {
                            logger.w(TAG, "cancelOrder: $NO_ACCOUNTS")
                            AppResult.Failure(AppError.Unknown(NO_ACCOUNTS, null))
                        } else {
                            val accountId = accounts.first().id
                            val cancelResult: AppResult<Unit> =
                                if (order is Order.Stop) {
                                    ordersRepository.cancelStopOrderResult(accountId, order.orderId)
                                } else {
                                    ordersRepository.cancelOrderResult(brokerName, accountId, order.orderId)
                                }
                            when (cancelResult) {
                                is AppResult.Success -> {
                                    logger.d(TAG, "cancelOrder: success for orderId=${order.orderId}")
                                    AppResult.Success(Unit)
                                }
                                is AppResult.Failure -> {
                                    logger.e(
                                        TAG,
                                        "cancelOrder failed: ${cancelResult.error.message}",
                                        cancelResult.error.cause,
                                    )
                                    cancelResult
                                }
                            }
                        }
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
                logger.e(TAG, "cancel order exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка отмены: ${e.message}", e))
            }
        }
    }
