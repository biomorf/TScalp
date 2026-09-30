package com.example.tscalp.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        brokerName: String,
        sandboxMode: Boolean
    ): AppResult<List<BrokerAccount>> = withContext(Dispatchers.IO) {
        val name = BrokerName.fromKey(brokerName)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Неизвестный брокер: $brokerName")
            )
        val broker = brokerManager.getBroker(name)
            ?: return@withContext AppResult.Failure(
                AppError.Unknown("Брокер $brokerName не зарегистрирован")
            )
        runCatchingAppResult { broker.getAccounts(sandboxMode) }
    }

    /**
     * Устаревшая версия: возвращает список или бросает исключение.
     * Новый код должен использовать [getAccountsResult].
     */
    @Deprecated(
        message = "Use getAccountsResult() to handle errors explicitly",
        replaceWith = ReplaceWith("getAccountsResult(brokerName, sandboxMode)")
    )
    suspend fun getAccounts(
        brokerName: String,
        sandboxMode: Boolean
    ): List<BrokerAccount> {
        return when (val result = getAccountsResult(brokerName, sandboxMode)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> throw IllegalStateException(
                result.error.message,
                result.error.cause
            )
        }
    }

    /**
     * ///Поиск инструментов – возвращает список InstrumentUi, готовых для UI.
     * ///Если не удалось получить полный Instrument, поля currency и lot останутся по умолчанию.
     */
    suspend fun searchInstruments(query: String): List<InstrumentUi> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        broker.findInstruments(query)
    }

    /**
     * Получает последние цены для списка тикеров.
     * Внутри вызывает resolveBrokerTicker для каждого тикера и запрашивает цены через брокера.
     */
    suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?> = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        broker.getLastPricesByTscalpInstrumentId(ids)
    }

    suspend fun getBalance(accountId: String): Double = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        broker.getBalance(accountId)
    }

    suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney) {
        Log.d("InvestRepository", "Вызов sandboxPayIn для счета $accountId, сумма ${amount.units} ${amount.currency}")
        val broker = brokerManager.getDefaultBroker()
        broker.sandboxPayIn(accountId, amount)
    }

    /**
     * Отправляет заявку (рыночную или лимитную) через указанного брокера.
     * Типизированный результат: AppResult.Success(OrderResult) или AppResult.Failure(AppError).
     */
    suspend fun postOrderResult(request: BrokerOrderRequest): AppResult<OrderResult> =
        withContext(Dispatchers.IO) {
            val name = BrokerName.fromKey(request.brokerName)
                ?: return@withContext AppResult.Failure(
                    AppError.Unknown("Неизвестный брокер: ${request.brokerName}")
                )
            val broker = brokerManager.getBroker(name)
                ?: return@withContext AppResult.Failure(
                    AppError.Unknown("Брокер ${request.brokerName} не зарегистрирован")
                )
            runCatchingAppResult { broker.postOrder(request) }
        }

    /**
     * Выставляет стоп-заявку (take-profit, stop-loss, stop-limit).
     * Типизированный результат: AppResult.Success(stopOrderId) или AppResult.Failure(AppError).
     */
    suspend fun postStopOrderResult(request: StopOrderRequest): AppResult<String> =
        withContext(Dispatchers.IO) {
            val name = BrokerName.fromKey(request.brokerName)
                ?: return@withContext AppResult.Failure(
                    AppError.Unknown("Неизвестный брокер: ${request.brokerName}")
                )
            val broker = brokerManager.getBroker(name)
                ?: return@withContext AppResult.Failure(
                    AppError.Unknown("Брокер ${request.brokerName} не зарегистрирован")
                )
            runCatchingAppResult { broker.postStopOrder(request) }
        }

    /**
     * Устаревшая версия: возвращает OrderResult или бросает исключение.
     * Новый код должен использовать [postOrderResult].
     */
    @Deprecated(
        message = "Use postOrderResult() to handle errors explicitly",
        replaceWith = ReplaceWith("postOrderResult(request)")
    )
    suspend fun postOrder(request: BrokerOrderRequest): OrderResult {
        return when (val result = postOrderResult(request)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> throw IllegalStateException(
                result.error.message,
                result.error.cause
            )
        }
    }

    /**
     * Устаревшая версия: возвращает stopOrderId или бросает исключение.
     * Новый код должен использовать [postStopOrderResult].
     */
    @Deprecated(
        message = "Use postStopOrderResult() to handle errors explicitly",
        replaceWith = ReplaceWith("postStopOrderResult(request)")
    )
    suspend fun postStopOrder(request: StopOrderRequest): String {
        return when (val result = postStopOrderResult(request)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> throw IllegalStateException(
                result.error.message,
                result.error.cause
            )
        }
    }

    suspend fun cancelStopOrder(accountId: String, orderId: String) = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        broker.cancelStopOrder(accountId, orderId)
    }
}
