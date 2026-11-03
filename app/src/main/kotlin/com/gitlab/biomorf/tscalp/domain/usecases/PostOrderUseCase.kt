package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.OrdersRepository
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.domain.models.StopOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Входные данные торговой операции: всё, что VM получает из `OrdersUiState`
 * и резолвит из `lastSelectedInstruments` / `selectedInstrument`.
 *
 * Presentation-поля (string-цены из TextField, форматирование, UI-состояние)
 * сюда не входят. VM валидирует и передаёт сюда уже резолвнутые domain-значения.
 */
data class PostOrderInput(
    val brokerName: BrokerName,
    val ticker: String,
    val tscalpInstrumentId: String,
    val quantity: Long,
    val direction: OrderDirection,
    val accountId: String,
    val sandboxMode: Boolean,
    val orderType: OrderTypeSelection,
    val limitPrice: String?,
    val stopPrice: String?,
    val expirationType: StopOrderExpirationType,
    val paired: PairedOrderInput?,
)

/** Парная сделка: контр-заявка при `pairTradingEnabled`. */
data class PairedOrderInput(
    val tscalpInstrumentId: String,
    val ticker: String,
    val brokerName: BrokerName,
    val accountId: String,
    val multiplier: String?,
)

/**
 * Структурированный результат торговой операции. Без presentation-форматирования:
 * сообщения (✅/❌, форматирование валют) строит VM.
 */
sealed interface PostOrderOutcome {
    /** Успешное исполнение основной (и, если есть, парной) заявки. */
    data class Success(
        val primary: PrimaryOutcome,
        val paired: PairedOutcome?,
    ) : PostOrderOutcome

    /** `checkTradeAvailability` вернул `InsufficientFunds`. */
    data class InsufficientFunds(
        val balance: Double,
        val required: Double,
    ) : PostOrderOutcome

    /** Ошибка checkTradeAvailability / prepare / post (network, API, unexpected). */
    data class Error(
        val message: String,
        val cause: Throwable?,
    ) : PostOrderOutcome

    /** Validation-ошибка до API: не хватает ticker / accountId / tscalpInstrumentId. */
    data class InvalidRequest(
        val reason: String,
    ) : PostOrderOutcome
}

/** Итог основной заявки. */
sealed interface PrimaryOutcome {
    data class Regular(
        val orderId: String,
        val executedLots: Long,
        val totalLots: Long,
    ) : PrimaryOutcome

    data class Stop(
        val orderId: String,
    ) : PrimaryOutcome
}

/** Итог парной заявки (не блокирует основную). */
sealed interface PairedOutcome {
    data class Regular(
        val ticker: String,
        val orderId: String,
        val quantity: Long,
    ) : PairedOutcome

    data class Stop(
        val ticker: String,
        val orderId: String,
        val quantity: Long,
    ) : PairedOutcome

    data class Failed(
        val message: String,
        val cause: Throwable?,
    ) : PairedOutcome
}

/**
 * Внутренний результат `checkAvailability`: `Success` — можно продолжать,
 * `InsufficientFunds` / `Error` — провал check. Наружу не выходит, маппится
 * в `PostOrderOutcome` в `execute`.
 */
internal sealed interface CheckResult {
    data object Success : CheckResult

    data class InsufficientFunds(
        val balance: Double,
        val required: Double,
    ) : CheckResult

    data class Error(
        val message: String,
        val cause: Throwable?,
    ) : CheckResult
}

/**
 * Оркестрация торговой операции: проверка доступности → подготовка
 * запросов → отправка основной → отправка парной (опционально).
 *
 * Не обновляет UI-состояние, не форматирует сообщения, не логирует.
 * Всё presentation — в `OrdersViewModel` (см. `### OrdersViewModel: миграция
 * на PortfolioSheet`).
 */
class PostOrderUseCase
    @Inject
    constructor(
        private val ordersRepository: OrdersRepository,
        private val prepareOrderRequest: PrepareOrderRequestUseCase,
    ) {
        /**
         * Выполняет торговую операцию по [input]. Возвращает [PostOrderOutcome].
         * Никогда не бросает (кроме `CancellationException` — пробрасывается).
         */
        suspend fun execute(input: PostOrderInput): PostOrderOutcome {
            if (input.ticker.isBlank() || input.accountId.isBlank() || input.tscalpInstrumentId.isBlank()) {
                return PostOrderOutcome.InvalidRequest(
                    "Не хватает ticker / accountId / tscalpInstrumentId",
                )
            }

            when (val check = checkAvailability(input)) {
                is CheckResult.Success -> Unit
                is CheckResult.InsufficientFunds ->
                    return PostOrderOutcome.InsufficientFunds(
                        balance = check.balance,
                        required = check.required,
                    )
                is CheckResult.Error ->
                    return PostOrderOutcome.Error(
                        message = check.message,
                        cause = check.cause,
                    )
            }

            val prepared =
                try {
                    prepareOrderRequest.prepare(
                        brokerName = input.brokerName,
                        ticker = input.ticker,
                        tscalpInstrumentId = input.tscalpInstrumentId,
                        quantity = input.quantity,
                        direction = input.direction,
                        accountId = input.accountId,
                        sandboxMode = input.sandboxMode,
                        orderType = input.orderType,
                        limitPrice = input.limitPrice,
                        stopPrice = input.stopPrice,
                        expirationType = input.expirationType,
                        pairedTscalpInstrumentId = input.paired?.tscalpInstrumentId,
                        pairedTicker = input.paired?.ticker,
                        pairedBrokerName = input.paired?.brokerName,
                        pairedAccountId = input.paired?.accountId,
                        pairedMultiplier = input.paired?.multiplier,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return PostOrderOutcome.Error(
                        message = e.message ?: "Ошибка подготовки заявки",
                        cause = e,
                    )
                }

            val primary =
                when (val r = postPrimary(prepared)) {
                    is PostOrderOutcome.Error -> return r
                    is PostOrderOutcome.Success -> r.primary
                    else -> return PostOrderOutcome.Error("Unexpected primary outcome", null)
                }

            val paired = if (prepared.pairedRequest != null) postPaired(prepared) else null

            return PostOrderOutcome.Success(primary = primary, paired = paired)
        }

        /**
         * `checkTradeAvailabilityResult`. `CheckResult.Success` — можно продолжать;
         * `InsufficientFunds` / `Error` — провал check (маппится в `PostOrderOutcome` в [execute]).
         */
        private suspend fun checkAvailability(input: PostOrderInput): CheckResult {
            val checkResult =
                when (
                    val result =
                        ordersRepository.checkTradeAvailabilityResult(
                            brokerName = input.brokerName,
                            accountId = input.accountId,
                            tscalpInstrumentId = input.tscalpInstrumentId,
                            direction = input.direction,
                            quantity = input.quantity,
                        )
                ) {
                    is AppResult.Success -> result.data
                    is AppResult.Failure ->
                        return CheckResult.Error(
                            message = result.error.message,
                            cause = result.error.cause,
                        )
                }
            return when (checkResult) {
                is TradeCheckResult.Success -> CheckResult.Success
                is TradeCheckResult.InsufficientFunds ->
                    CheckResult.InsufficientFunds(
                        balance = checkResult.balance,
                        required = checkResult.required,
                    )
                is TradeCheckResult.Error -> CheckResult.Error(checkResult.message, null)
            }
        }

        /** Отправляет основную заявку. */
        private suspend fun postPrimary(prepared: PrepareOrderRequestUseCase.PreparedOrders): PostOrderOutcome =
            try {
                if (prepared.isPrimaryStop) {
                    when (val r = ordersRepository.postStopOrderResult(prepared.primaryRequest as StopOrderRequest)) {
                        is AppResult.Success ->
                            PostOrderOutcome.Success(PrimaryOutcome.Stop(r.data), null)
                        is AppResult.Failure ->
                            PostOrderOutcome.Error(r.error.message, r.error.cause)
                    }
                } else {
                    when (val r = ordersRepository.postOrderResult(prepared.primaryRequest as BrokerOrderRequest)) {
                        is AppResult.Success ->
                            PostOrderOutcome.Success(
                                PrimaryOutcome.Regular(r.data.orderId, r.data.executedLots, r.data.totalLots),
                                null,
                            )
                        is AppResult.Failure ->
                            PostOrderOutcome.Error(r.error.message, r.error.cause)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PostOrderOutcome.Error(e.message ?: "Ошибка отправки основной заявки", e)
            }

        /** Отправляет парную заявку. Failure → `PairedOutcome.Failed` (не блокирует основную). */
        private suspend fun postPaired(prepared: PrepareOrderRequestUseCase.PreparedOrders): PairedOutcome =
            try {
                if (prepared.isPairedStop == true) {
                    val req = prepared.pairedRequest as StopOrderRequest
                    when (val r = ordersRepository.postStopOrderResult(req)) {
                        is AppResult.Success ->
                            PairedOutcome.Stop(ticker = req.ticker, orderId = r.data, quantity = req.quantity)
                        is AppResult.Failure ->
                            PairedOutcome.Failed(r.error.message, r.error.cause)
                    }
                } else {
                    val req = prepared.pairedRequest as BrokerOrderRequest
                    when (val r = ordersRepository.postOrderResult(req)) {
                        is AppResult.Success ->
                            PairedOutcome.Regular(ticker = req.ticker, orderId = r.data.orderId, quantity = req.quantity)
                        is AppResult.Failure ->
                            PairedOutcome.Failed(r.error.message, r.error.cause)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PairedOutcome.Failed(e.message ?: "Ошибка контрсделки", e)
            }
    }
