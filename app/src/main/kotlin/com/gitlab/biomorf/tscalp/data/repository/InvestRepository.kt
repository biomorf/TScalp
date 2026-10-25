package com.gitlab.biomorf.tscalp.data.repository

import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.BrokerOrderRequest
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.TradeCheckResult
import com.gitlab.biomorf.tscalp.util.AppLogger
import com.gitlab.biomorf.tscalp.util.runCatchingAppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * ///Репозиторий – преобразует контракты API в доменные модели приложения.
 *
 * Архитектурный долг: 17 функций в классе.
 * См. ROADMAP → «Рефакторинг репозиториев».
 */
@Suppress("TooManyFunctions")
class InvestRepository(
    private val brokerManager: BrokerManager,
) {
    companion object {
        private const val TAG = "InvestRepository"
    }

    /**
     * Получает счета для указанного брокера.
     * Типизированный результат: AppResult.Success со списком или AppResult.Failure с AppError.
     */
    suspend fun getAccountsResult(
        brokerName: BrokerName,
        sandboxMode: Boolean,
    ): AppResult<List<BrokerAccount>> =
        withContext(Dispatchers.IO) {
            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                    )
            val accountsApi =
                broker.accounts
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает счета"),
                    )
            runCatchingAppResult(brokerName) { accountsApi.getAccounts(sandboxMode) }
        }

    /**
     * ///Поиск инструментов – возвращает список InstrumentUi, готовых для UI.
     * ///Если не удалось получить полный Instrument, поля currency и lot останутся по умолчанию.
     */
    suspend fun searchInstrumentsResult(query: String): AppResult<List<InstrumentUi>> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getDefaultBroker()
            val userInstrumentsApi =
                broker.userInstruments
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${BrokerName.TINVEST.displayName} не поддерживает поиск инструментов"),
                    )
            runCatchingAppResult(BrokerName.TINVEST) { userInstrumentsApi.findInstruments(query) }
        }

    /**
     * Последние цены по списку tscalpInstrumentId.
     * Типизированный результат: AppResult.Success(Map<tscalpInstrumentId, price?>) или AppResult.Failure.
     */
    suspend fun getLastPricesResult(ids: List<String>): AppResult<Map<String, Double?>> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getDefaultBroker()
            runCatchingAppResult(BrokerName.TINVEST) { broker.getLastPricesByTscalpInstrumentId(ids) }
        }

    /**
     * Свободный остаток по счёту.
     * Типизированный результат: AppResult.Success(баланс) или AppResult.Failure.
     */
    suspend fun getBalanceResult(accountId: String): AppResult<Double> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getDefaultBroker()
            val balanceApi =
                broker.balance
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${BrokerName.TINVEST.displayName} не поддерживает баланс"),
                    )
            runCatchingAppResult(BrokerName.TINVEST) { balanceApi.getBalance(accountId) }
        }

    /**
     * Пополнение счёта в песочнице.
     * Типизированный результат: AppResult.Success(Unit) или AppResult.Failure.
     */
    suspend fun sandboxPayInResult(
        accountId: String,
        amount: SandboxMoney,
    ): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            AppLogger.d(TAG, "sandboxPayIn: accountId=$accountId, amount=${amount.units} ${amount.currency}")
            val broker = brokerManager.getDefaultBroker()
            val sandboxApi =
                broker.sandbox
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${BrokerName.TINVEST.displayName} не поддерживает песочницу"),
                    )
            runCatchingAppResult(BrokerName.TINVEST) {
                sandboxApi.sandboxPayIn(accountId, amount)
                Unit
            }
        }

    /**
     * Отправляет заявку (рыночную или лимитную) через указанного брокера.
     * Типизированный результат: AppResult.Success(OrderResult) или AppResult.Failure(AppError).
     */
    suspend fun postOrderResult(request: BrokerOrderRequest): AppResult<OrderResult> =
        withContext(Dispatchers.IO) {
            val broker =
                brokerManager.getBroker(request.brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${request.brokerName.displayName} не зарегистрирован"),
                    )
            val ordersApi =
                broker.orders
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${request.brokerName.displayName} не поддерживает заявки"),
                    )
            runCatchingAppResult(request.brokerName) { ordersApi.postOrder(request) }
        }

    /**
     * Выставляет стоп-заявку (take-profit, stop-loss, stop-limit).
     * Типизированный результат: AppResult.Success(stopOrderId) или AppResult.Failure(AppError).
     */
    suspend fun postStopOrderResult(request: StopOrderRequest): AppResult<String> =
        withContext(Dispatchers.IO) {
            val broker =
                brokerManager.getBroker(request.brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${request.brokerName.displayName} не зарегистрирован"),
                    )
            val ordersApi =
                broker.orders
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${request.brokerName.displayName} не поддерживает заявки"),
                    )
            runCatchingAppResult(request.brokerName) { ordersApi.postStopOrder(request) }
        }

    /**
     * Отмена стоп-заявки.
     * Типизированный результат: AppResult.Success(Unit) или AppResult.Failure.
     */
    suspend fun cancelStopOrderResult(
        accountId: String,
        orderId: String,
    ): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getDefaultBroker()
            val ordersApi =
                broker.orders
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${BrokerName.TINVEST.displayName} не поддерживает заявки"),
                    )
            runCatchingAppResult(BrokerName.TINVEST) {
                ordersApi.cancelStopOrder(accountId, orderId)
                Unit
            }
        }

    /**
     * Позиции портфеля у указанного брокера.
     * Типизированный результат: AppResult.Success(List<PortfolioPosition>) или AppResult.Failure.
     */
    suspend fun fetchPositionsResult(
        brokerName: BrokerName,
        accountId: String,
        sandboxMode: Boolean,
    ): AppResult<List<PortfolioPosition>> =
        withContext(Dispatchers.IO) {
            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                    )
            runCatchingAppResult(brokerName) { broker.fetchPositionsSnapshot(accountId, sandboxMode) }
        }

    /**
     * Статусы доступности инструментов.
     * Типизированный результат: AppResult.Success(Map<tscalpInstrumentId, TradingAvailability>) или AppResult.Failure.
     */
    suspend fun getTradingStatusesResult(
        brokerName: BrokerName,
        ids: List<String>,
    ): AppResult<Map<String, TradingAvailability>> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext AppResult.Success(emptyMap())
            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                    )
            val userInstrumentsApi =
                broker.userInstruments
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает статусы торговли"),
                    )
            runCatchingAppResult(brokerName) { userInstrumentsApi.getTradingStatuses(ids) }
        }

    /**
     * Все активные заявки (обычные + стоп) по счёту.
     * Типизированный результат: AppResult.Success(List<OrderListItem>) или AppResult.Failure.
     * Сортировка — задача вызывающего слоя (presentation).
     */
    suspend fun getAllOrdersResult(
        brokerName: BrokerName,
        accountId: String,
    ): AppResult<List<OrderListItem>> =
        withContext(Dispatchers.IO) {
            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                    )
            val ordersApi =
                broker.orders
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает заявки"),
                    )
            runCatchingAppResult(brokerName) {
                ordersApi.getOrders(accountId) + ordersApi.getStopOrders(accountId)
            }
        }

    /**
     * Отмена обычной (не стоп) заявки.
     * Типизированный результат: AppResult.Success(Unit) или AppResult.Failure.
     */
    suspend fun cancelOrderResult(
        brokerName: BrokerName,
        accountId: String,
        orderId: String,
    ): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                    )
            val ordersApi =
                broker.orders
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает заявки"),
                    )
            runCatchingAppResult(brokerName) {
                ordersApi.cancelOrder(accountId, orderId)
                Unit
            }
        }

    /**
     * Стрим позиций. Ошибки идут через поток — вызывающий ловит их оператором .catch {}.
     */
    fun subscribePositions(
        brokerName: BrokerName,
        accountId: String,
    ): Flow<PositionStreamItem> =
        flow {
            val broker =
                brokerManager.getBroker(brokerName)
                    ?: throw IllegalStateException("Брокер ${brokerName.displayName} не зарегистрирован")
            emitAll(broker.subscribePositions(accountId))
        }

    /**
     * Предварительная проверка возможности сделки: свободный остаток
     * и наличие цены по инструменту. Реализуется брокером.
     * Типизированный результат: AppResult.Success(TradeCheckResult) или AppResult.Failure.
     */
    suspend fun checkTradeAvailabilityResult(
        brokerName: BrokerName,
        accountId: String,
        tscalpInstrumentId: String,
        direction: OrderDirection,
        quantity: Long,
    ): AppResult<TradeCheckResult> =
        withContext(Dispatchers.IO) {
            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                    )
            runCatchingAppResult(brokerName) {
                broker.checkTradeAvailability(
                    accountId = accountId,
                    tscalpInstrumentId = tscalpInstrumentId,
                    brokerUid = tscalpInstrumentId,
                    direction = direction,
                    quantity = quantity,
                )
            }
        }

    /**
     * Открывает новый счёт в песочнице у указанного брокера.
     * Типизированный результат: AppResult.Success(accountId) или AppResult.Failure.
     */
    suspend fun openSandboxAccountResult(
        brokerName: BrokerName,
    ): AppResult<String> =
        withContext(Dispatchers.IO) {
            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                    )
            val sandboxApi =
                broker.sandbox
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает песочницу"),
                    )
            runCatchingAppResult(brokerName) { sandboxApi.openSandboxAccount() }
        }

    /**
     * Закрывает счёт в песочнице у указанного брокера.
     * Типизированный результат: AppResult.Success(Unit) или AppResult.Failure.
     */
    suspend fun closeSandboxAccountResult(
        brokerName: BrokerName,
        accountId: String,
    ): AppResult<Unit> =
        withContext(Dispatchers.IO) {
            val broker =
                brokerManager.getBroker(brokerName)
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован"),
                    )
            val sandboxApi =
                broker.sandbox
                    ?: return@withContext AppResult.Failure(
                        AppError.Unknown("Брокер ${brokerName.displayName} не поддерживает песочницу"),
                    )
            runCatchingAppResult(brokerName) {
                sandboxApi.closeSandboxAccount(accountId)
                Unit
            }
        }
}
