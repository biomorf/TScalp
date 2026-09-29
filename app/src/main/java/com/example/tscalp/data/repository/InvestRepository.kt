package com.example.tscalp.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.example.tscalp.domain.models.BrokerAccount
import com.example.tscalp.domain.models.BrokerOrderRequest
import com.example.tscalp.di.BrokerManager
import com.example.tscalp.domain.models.BrokerName
import com.example.tscalp.domain.models.*


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
     * Получает счета для брокера по умолчанию (TInvest).
     * Оставлен для совместимости с существующим кодом.
     */
    suspend fun getAccounts(brokerName: String, sandboxMode: Boolean): List<BrokerAccount> = withContext(Dispatchers.IO) {
        val name = BrokerName.fromKey(brokerName)
            ?: throw IllegalArgumentException("Неизвестный брокер: $brokerName")
        val broker = brokerManager.getBroker(name)
            ?: throw IllegalArgumentException("Брокер $brokerName не зарегистрирован")
        broker.getAccounts(sandboxMode)
    }

//     suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> = withContext(Dispatchers.IO) {
//        val broker = brokerManager.getDefaultBroker()
//        broker.getPositions(accountId, sandboxMode)
//    }

    /**
     * Возвращает специфичный для брокера идентификатор (figi, uid и т.д.) по тикеру.
     * @param brokerName имя брокера
     * @param ticker тикер инструмента
     */
//    suspend fun resolveBrokerTicker(brokerName: String, ticker: String): String? {
//        val broker = brokerManager.getBroker(brokerName) ?: return null
//        return broker.resolveTicker(ticker)
//    }

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
