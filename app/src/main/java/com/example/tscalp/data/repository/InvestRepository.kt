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
     */
    suspend fun postOrder(request: BrokerOrderRequest): OrderResult = withContext(Dispatchers.IO) {
        val name = BrokerName.fromKey(request.brokerName)
            ?: throw IllegalArgumentException("Неизвестный брокер: ${request.brokerName}")
        val broker = brokerManager.getBroker(name)
            ?: throw IllegalArgumentException("Брокер ${request.brokerName} не зарегистрирован")
        broker.postOrder(request)
    }

    suspend fun postStopOrder(request: StopOrderRequest): String = withContext(Dispatchers.IO) {
        val name = BrokerName.fromKey(request.brokerName)
            ?: throw IllegalArgumentException("Неизвестный брокер: ${request.brokerName}")
        val broker = brokerManager.getBroker(name)
            ?: throw IllegalArgumentException("Брокер ${request.brokerName} не зарегистрирован")
        broker.postStopOrder(request)
    }

    suspend fun cancelStopOrder(accountId: String, orderId: String) = withContext(Dispatchers.IO) {
        val broker = brokerManager.getDefaultBroker()
        broker.cancelStopOrder(accountId, orderId)
    }
}
