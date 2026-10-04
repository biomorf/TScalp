package com.example.tscalp.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

import com.example.tscalp.util.AppLogger
import com.example.tscalp.domain.models.BrokerAccount
import com.example.tscalp.domain.models.BrokerOrderRequest
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.BrokerName
import com.example.tscalp.domain.models.AppError
import com.example.tscalp.domain.models.AppResult
import com.example.tscalp.domain.models.OrderDirection
import com.example.tscalp.domain.models.TradeCheckResult
import com.example.tscalp.domain.models.*
import com.example.tscalp.util.runCatchingAppResult


/**
 * ///Репозиторий – преобразует контракты API в доменные модели приложения.
 */
class InvestRepository(
    private val brokerManager: BrokerManager
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
        sandboxMode: Boolean
    ): AppResult<List<BrokerAccount>> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован")
            )
        runCatchingAppResult(brokerName) { broker.getAccounts(sandboxMode) }
    }

    /**
     * ///Поиск инструментов – возвращает список InstrumentUi, готовых для UI.
     * ///Если не удалось получить полный Instrument, поля currency и lot останутся по умолчанию.
     */
    suspend fun searchInstrumentsResult(query: String): AppResult<List<InstrumentUi>> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getDefaultBroker()
            runCatchingAppResult(BrokerName.TINVEST) { broker.findInstruments(query) }
        }

    /**
     * Последние цены по списку uid.
     * Типизированный результат: AppResult.Success(Map<uid, price?>) или AppResult.Failure.
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
            runCatchingAppResult(BrokerName.TINVEST) { broker.getBalance(accountId) }
        }

    /**
     * Пополнение счёта в песочнице.
     * Типизированный результат: AppResult.Success(Unit) или AppResult.Failure.
     */
    suspend fun sandboxPayInResult(
        accountId: String,
        amount: SandboxMoney
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "sandboxPayIn: accountId=$accountId, amount=${amount.units} ${amount.currency}")
        val broker = brokerManager.getDefaultBroker()
        runCatchingAppResult(BrokerName.TINVEST) { broker.sandboxPayIn(accountId, amount); Unit }
    }

    /**
     * Отправляет заявку (рыночную или лимитную) через указанного брокера.
     * Типизированный результат: AppResult.Success(OrderResult) или AppResult.Failure(AppError).
     */
    suspend fun postOrderResult(request: BrokerOrderRequest): AppResult<OrderResult> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getBroker(request.brokerName)
                ?: return@withContext AppResult.Failure(
                    AppError.Unknown("Брокер ${request.brokerName.displayName} не зарегистрирован")
                )
            runCatchingAppResult(request.brokerName) { broker.postOrder(request) }
        }

    /**
     * Выставляет стоп-заявку (take-profit, stop-loss, stop-limit).
     * Типизированный результат: AppResult.Success(stopOrderId) или AppResult.Failure(AppError).
     */
    suspend fun postStopOrderResult(request: StopOrderRequest): AppResult<String> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getBroker(request.brokerName)
                ?: return@withContext AppResult.Failure(
                    AppError.Unknown("Брокер ${request.brokerName.displayName} не зарегистрирован")
                )
            runCatchingAppResult(request.brokerName) { broker.postStopOrder(request) }
        }

    /**
     * Отмена стоп-заявки.
     * Типизированный результат: AppResult.Success(Unit) или AppResult.Failure.
     */
    suspend fun cancelStopOrderResult(
        accountId: String,
        orderId: String
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        runCatchingAppResult(BrokerName.TINVEST) { broker.cancelStopOrder(accountId, orderId); Unit }
    }

    /**
     * Позиции портфеля у указанного брокера.
     * Типизированный результат: AppResult.Success(List<PortfolioPosition>) или AppResult.Failure.
     */
    suspend fun fetchPositionsResult(
        brokerName: BrokerName,
        accountId: String,
        sandboxMode: Boolean
    ): AppResult<List<PortfolioPosition>> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован")
            )
        runCatchingAppResult(brokerName) { broker.fetchPositionsRest(accountId, sandboxMode) }
    }

    /**
     * Статусы доступности инструментов.
     * Типизированный результат: AppResult.Success(Map<uid, TradingAvailability>) или AppResult.Failure.
     */
    suspend fun getTradingStatusesResult(
        brokerName: BrokerName,
        ids: List<String>
    ): AppResult<Map<String, TradingAvailability>> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext AppResult.Success(emptyMap())
        val broker = brokerManager.getBroker(brokerName)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован")
            )
        runCatchingAppResult(brokerName) { broker.getTradingStatuses(ids) }
    }

    /**
     * Все активные заявки (обычные + стоп) по счёту.
     * Типизированный результат: AppResult.Success(List<OrderListItem>) или AppResult.Failure.
     * Сортировка — задача вызывающего слоя (presentation).
     */
    suspend fun getAllOrdersResult(
        brokerName: BrokerName,
        accountId: String
    ): AppResult<List<OrderListItem>> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован")
            )
        runCatchingAppResult(brokerName) {
            broker.getOrders(accountId) + broker.getStopOrders(accountId)
        }
    }

    /**
     * Отмена обычной (не стоп) заявки.
     * Типизированный результат: AppResult.Success(Unit) или AppResult.Failure.
     */
    suspend fun cancelOrderResult(
        brokerName: BrokerName,
        accountId: String,
        orderId: String
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован")
            )
        runCatchingAppResult(brokerName) { broker.cancelOrder(accountId, orderId); Unit }
    }

    /**
     * Стрим последних цен. Ошибки (включая отсутствие брокера)
     * идут через поток — вызывающий ловит их оператором .catch {}.
     */
    fun subscribeLastPrices(
        brokerName: BrokerName,
        ids: List<String>
    ): Flow<Pair<String, Double>> = flow {
        val broker = brokerManager.getBroker(brokerName)
            ?: throw IllegalStateException("Брокер ${brokerName.displayName} не зарегистрирован")
        emitAll(broker.subscribeLastPrices(ids))
    }

    /**
     * Стрим позиций. Ошибки идут через поток — вызывающий ловит их оператором .catch {}.
     */
    fun subscribePositions(
        brokerName: BrokerName,
        accountId: String
    ): Flow<PositionStreamItem> = flow {
        val broker = brokerManager.getBroker(brokerName)
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
        uid: String,
        direction: OrderDirection,
        quantity: Long
    ): AppResult<TradeCheckResult> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован")
            )
        runCatchingAppResult(brokerName) {
            broker.checkTradeAvailability(
                accountId = accountId,
                tscalpInstrumentId = uid,
                uid = uid,
                direction = direction,
                quantity = quantity
            )
        }
    }

    /**
     * Открывает новый счёт в песочнице у указанного брокера.
     * Типизированный результат: AppResult.Success(accountId) или AppResult.Failure.
     */
    suspend fun openSandboxAccountResult(
        brokerName: BrokerName
    ): AppResult<String> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован")
            )
        runCatchingAppResult(brokerName) { broker.openSandboxAccount() }
    }

    /**
     * Закрывает счёт в песочнице у указанного брокера.
     * Типизированный результат: AppResult.Success(Unit) или AppResult.Failure.
     */
    suspend fun closeSandboxAccountResult(
        brokerName: BrokerName,
        accountId: String
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getBroker(brokerName)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Брокер ${brokerName.displayName} не зарегистрирован")
            )
        runCatchingAppResult(brokerName) { broker.closeSandboxAccount(accountId); Unit }
    }
}
