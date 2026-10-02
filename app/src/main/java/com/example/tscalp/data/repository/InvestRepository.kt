package com.example.tscalp.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.example.tscalp.util.AppLogger
import com.example.tscalp.domain.models.BrokerAccount
import com.example.tscalp.domain.models.BrokerOrderRequest
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.BrokerName
import com.example.tscalp.domain.models.AppError
import com.example.tscalp.domain.models.AppResult
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
        runCatchingAppResult { broker.getAccounts(sandboxMode) }
    }

    /**
     * ///Поиск инструментов – возвращает список InstrumentUi, готовых для UI.
     * ///Если не удалось получить полный Instrument, поля currency и lot останутся по умолчанию.
     */
    suspend fun searchInstrumentsResult(query: String): AppResult<List<InstrumentUi>> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getDefaultBroker()
            runCatchingAppResult { broker.findInstruments(query) }
        }

    /**
     * Последние цены по списку uid.
     * Типизированный результат: AppResult.Success(Map<uid, price?>) или AppResult.Failure.
     */
    suspend fun getLastPricesResult(ids: List<String>): AppResult<Map<String, Double?>> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getDefaultBroker()
            runCatchingAppResult { broker.getLastPricesByTscalpInstrumentId(ids) }
        }

    /**
     * Свободный остаток по счёту.
     * Типизированный результат: AppResult.Success(баланс) или AppResult.Failure.
     */
    suspend fun getBalanceResult(accountId: String): AppResult<Double> =
        withContext(Dispatchers.IO) {
            val broker = brokerManager.getDefaultBroker()
            runCatchingAppResult { broker.getBalance(accountId) }
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
        runCatchingAppResult { broker.sandboxPayIn(accountId, amount); Unit }
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
            runCatchingAppResult { broker.postOrder(request) }
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
            runCatchingAppResult { broker.postStopOrder(request) }
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
        runCatchingAppResult { broker.cancelStopOrder(accountId, orderId); Unit }
    }
}
