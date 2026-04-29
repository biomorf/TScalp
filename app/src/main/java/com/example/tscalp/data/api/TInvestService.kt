package com.example.tscalp.data.api

import android.util.Log
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.InstrumentUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import com.example.tscalp.domain.models.*
import com.example.tscalp.domain.models.BrokerOrderType
import com.example.tscalp.domain.models.OrderDirection
import com.example.tscalp.domain.models.PortfolioPosition
import ru.tinkoff.piapi.contract.v1.*
import ru.ttech.piapi.core.InvestApi
import ru.tinkoff.piapi.contract.v1.PostStopOrderRequest
import ru.tinkoff.piapi.contract.v1.StopOrderDirection
import ru.tinkoff.piapi.contract.v1.StopOrderType as ProtoStopOrderType
import ru.tinkoff.piapi.contract.v1.StopOrderExpirationType as ProtoStopOrderExpirationType


/**
 * Реализация BrokerApi для брокера Т‑Инвестиции (Kotlin SDK).
 * Хранит ticker→figi кэш, самостоятельно управляет своим экземпляром InvestApi.
 */
class TInvestInvestService : BrokerApi {

    companion object {
        private const val TAG = "TInvestInvestService"
    }

    // Кэш ticker → figi для быстрой конвертации
    private val tickerToFigiCache = ConcurrentHashMap<String, String>()

    // Собственный экземпляр API, создаётся при инициализации
    @Volatile
    private var api: InvestApi? = null

    override val isInitialized: Boolean
        get() = api != null

    /**
     * Инициализирует клиент API заново (вызывается из UI при подключении).
     * Использует сохранённые в ServiceLocator токен и режим.
     */
    fun initializeFromSettings() {
        val token = ServiceLocator.getToken() ?: return
        val sandbox = ServiceLocator.isSandboxMode()
        val target = if (sandbox) {
            "sandbox-invest-public-api.tbank.ru:443"
        } else {
            "invest-public-api.tbank.ru:443"
        }
        api = InvestApi.createApi(InvestApi.defaultChannel(token, target))
        tickerToFigiCache.clear()
    }

    /**
     * Ищет figi по тикеру. Сначала проверяет кэш, затем делает запрос к API.
     */
    override suspend fun resolveTicker(ticker: String): String? {
        tickerToFigiCache[ticker]?.let { return it }
        val shortList = findInstrumentShorts(ticker)
        val figi = shortList.firstOrNull { it.ticker.equals(ticker, ignoreCase = true) }?.figi
        if (figi != null) {
            tickerToFigiCache[ticker] = figi
        }
        return figi
    }

    // Публичный метод, соответствующий интерфейсу BrokerApi
    override suspend fun findInstruments(query: String): List<InstrumentUi> = withContext(Dispatchers.IO) {
        val shorts = findInstrumentShorts(query)
        shorts.mapNotNull { short ->
            val instrument = getInstrumentByTicker(short.ticker)
            if (instrument != null) {
                InstrumentUi(
                    ticker = instrument.ticker,
                    name = instrument.name,
                    currency = instrument.currency,
                    lot = instrument.lot,
                    instrumentType = instrument.instrumentType,
                    figi = short.figi
                )
            } else null
        }
    }

    /**
     * Возвращает InstrumentUi по тикеру. Использует resolveTicker и затем getInstrumentByFigi.
     */
    override suspend fun getInstrumentByTicker(ticker: String): InstrumentUi? {
        val figi = resolveTicker(ticker) ?: return null
        val instrumentResponse = getInstrumentByFigi(figi)
        val inst = instrumentResponse.instrument
        return InstrumentUi(
            ticker = inst.ticker,
            figi = inst.figi,
            name = inst.name,
            currency = inst.currency,
            lot = inst.lot,
            instrumentType = inst.instrumentType ?: ""
        )
    }

    /**
     * Возвращает краткие результаты поиска (InstrumentShort) по строке запроса.
     * Используется для кэширования и resolveTicker.
     */
    // Приватный метод для внутреннего кэширования (использует protobuf)
    private suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
        currentApi.instrumentsServiceSync.findInstrument(request).instrumentsList
    }

    /**
     * Внутренний метод получения полной информации об инструменте по figi.
     */
    private suspend fun getInstrumentByFigi(figi: String): InstrumentResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = InstrumentRequest.newBuilder()
            .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_FIGI)
            .setId(figi)
            .build()
        currentApi.instrumentsServiceSync.getInstrumentBy(request)
    }

    override suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = GetAccountsRequest.getDefaultInstance()
        val accounts = if (sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxAccounts(request).accountsList
        } else {
            currentApi.usersServiceSync.getAccounts(request).accountsList
        }
        accounts.map { acc ->
            BrokerAccount(
                id = acc.id,
                name = acc.name,
                type = when (acc.typeValue) {
                    1 -> BrokerAccountType.BROKER
                    2 -> BrokerAccountType.IIS
                    3 -> BrokerAccountType.INVEST_BOX
                    else -> BrokerAccountType.OTHER
                }
            )
        }
    }

    suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = PortfolioRequest.newBuilder().setAccountId(accountId).build()
        return@withContext if (sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxPortfolio(request)
        } else {
            currentApi.operationsServiceSync.getPortfolio(request)
        }
    }

    override suspend fun getPositions(accountId: String, sandboxMode: Boolean): List<PortfolioPosition> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val response = getPortfolio(accountId, sandboxMode)

        response.positionsList.mapNotNull { pos ->
            // Получаем тикер: либо из самого ответа, либо резолвим через figi
            val ticker = pos.ticker.ifBlank { resolveTicker(pos.figi) ?: pos.figi }
            if (ticker.isBlank()) return@mapNotNull null

            val instrument = getInstrumentByTicker(ticker)
            val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
            val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
            val totalValue = currentPrice * quantity

            PortfolioPosition(
                name = instrument?.name ?: "",
                ticker = ticker,
                quantity = quantity,
                currentPrice = currentPrice,
                totalValue = totalValue,
                profit = 0.0,
                profitPercent = 0.0,
                instrumentType = instrument?.instrumentType ?: ""
            )
        }
    }

    override suspend fun getLastPricesByTicker(tickers: List<String>): Map<String, Double?> = withContext(Dispatchers.IO) {
        if (tickers.isEmpty()) return@withContext emptyMap()
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")

        // Резолвим тикеры в figi
        val tickerToFigi = mutableMapOf<String, String>()
        for (ticker in tickers) {
            resolveTicker(ticker)?.let { tickerToFigi[ticker] = it }
        }
        if (tickerToFigi.isEmpty()) return@withContext emptyMap()

        val figis = tickerToFigi.values.toList()
        val request = GetLastPricesRequest.newBuilder().addAllFigi(figis).build()
        val response = currentApi.marketDataServiceSync.getLastPrices(request)

        val result = mutableMapOf<String, Double?>()
        for (lastPrice in response.lastPricesList) {
            val originalTicker = tickerToFigi.entries.find { it.value == lastPrice.figi }?.key
            if (originalTicker != null) {
                val price = lastPrice.price?.let { it.units + it.nano / 1_000_000_000.0 }
                result[originalTicker] = price
            }
        }
        result
    }

//    override suspend fun getMarginAttributes(accountId: String): GetMarginAttributesResponse = withContext(Dispatchers.IO) {
//        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
//        val request = GetMarginAttributesRequest.newBuilder().setAccountId(accountId).build()
//        currentApi.usersServiceSync.getMarginAttributes(request)
//    }

    override suspend fun getBalance(accountId: String): Double = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")

        if (ServiceLocator.isSandboxMode()) {
            // В песочнице метод getMarginAttributes недоступен, а свободные рубли не отражаются в портфеле.
            // Баланс будет обновлён в PortfolioViewModel после загрузки портфеля как totalValue.
            // В песочнице запрашиваем портфель и берём стоимость валют (денежные средства)
            // В песочнице запрашиваем портфель и суммируем денежные средства + стоимость активов
            val portfolioRequest = PortfolioRequest.newBuilder().setAccountId(accountId).build()
            val portfolio = currentApi.sandboxServiceSync.getSandboxPortfolio(portfolioRequest)

            // 1. Денежные средства (totalAmountCurrencies)
            val money = portfolio.totalAmountCurrencies
            var balance = (money?.units ?: 0) + (money?.nano ?: 0) / 1_000_000_000.0

            // 2. Добавляем стоимость всех позиций (акции, облигации, фонды и т.д.)
            for (position in portfolio.positionsList) {
                val price = position.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                val quantity = position.quantity?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                balance += price * quantity
            }

            Log.d(TAG, "Баланс песочницы (деньги + активы) для счета $accountId: $balance")
            balance
        } else {
            // Боевой режим – ликвидный портфель (уже включает все активы и деньги)
            val request = GetMarginAttributesRequest.newBuilder().setAccountId(accountId).build()
            val response = currentApi.usersServiceSync.getMarginAttributes(request)
            val money = response.liquidPortfolio
            val balance = (money?.units ?: 0) + (money?.nano ?: 0) / 1_000_000_000.0
            Log.d(TAG, "Баланс для счета $accountId: $balance")
            balance
        }
    }

    override suspend fun sandboxPayIn(accountId: String, amount: SandboxMoney) {
        withContext(Dispatchers.IO) {
            val currentApi = api ?: throw IllegalStateException("API не инициализирован")
            Log.d(TAG, "Отправка запроса на пополнение счета $accountId на сумму ${amount.units} ${amount.currency}")
            val money = MoneyValue.newBuilder()
                .setCurrency(amount.currency)
                .setUnits(amount.units)
                .setNano(amount.nano)
                .build()
            val request = SandboxPayInRequest.newBuilder()
                .setAccountId(accountId)
                .setAmount(money)
                .build()
            val response = currentApi.sandboxServiceSync.sandboxPayIn(request)
            Log.d(TAG, "Пополнение выполнено успешно, ответ: $response")
        }
    }

    override suspend fun postOrder(request: BrokerOrderRequest): OrderResult = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val figi = resolveTicker(request.ticker) ?: throw IllegalArgumentException("Тикер ${request.ticker} не найден")

        // Преобразуем цену в protobuf Quotation
        val price = if (request.type == BrokerOrderType.LIMIT && request.price != null) {
            val units = request.price.toLong()
            val nano = ((request.price - units) * 1_000_000_000).toInt()
            Quotation.newBuilder().setUnits(units).setNano(nano).build()
        } else {
            Quotation.newBuilder().setUnits(0).setNano(0).build()
        }

        val apiOrderType = when (request.type) {
            BrokerOrderType.MARKET -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_MARKET
            BrokerOrderType.LIMIT -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_LIMIT
        }

        val apiDirection = when (request.direction) {
            OrderDirection.BUY -> ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY
            OrderDirection.SELL -> ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL
        }

        val apiRequest = PostOrderRequest.newBuilder()
            .setFigi(figi)
            .setQuantity(request.quantity)
            .setPrice(price)
            .setDirection(apiDirection)
            .setAccountId(request.accountId)
            .setOrderType(apiOrderType)
            .build()

        val response = if (request.sandboxMode) {
            currentApi.sandboxServiceSync.postSandboxOrder(apiRequest)
        } else {
            currentApi.ordersServiceSync.postOrder(apiRequest)
        }

        OrderResult(
            orderId = response.orderId,
            executedLots = response.lotsExecuted,
            totalLots = response.lotsRequested,
            status = OrderStatus.NEW
        )
    }

    // stop orders

    private fun protoDirection(direction: OrderDirection): ru.tinkoff.piapi.contract.v1.StopOrderDirection = when (direction) {
        OrderDirection.BUY -> ru.tinkoff.piapi.contract.v1.StopOrderDirection.STOP_ORDER_DIRECTION_BUY
        OrderDirection.SELL -> ru.tinkoff.piapi.contract.v1.StopOrderDirection.STOP_ORDER_DIRECTION_SELL
    }

    private fun protoStopOrderType(type: StopOrderType): ru.tinkoff.piapi.contract.v1.StopOrderType = when (type) {
        StopOrderType.TAKE_PROFIT -> ru.tinkoff.piapi.contract.v1.StopOrderType.STOP_ORDER_TYPE_TAKE_PROFIT
        StopOrderType.STOP_LOSS -> ru.tinkoff.piapi.contract.v1.StopOrderType.STOP_ORDER_TYPE_STOP_LOSS
        StopOrderType.STOP_LIMIT -> ru.tinkoff.piapi.contract.v1.StopOrderType.STOP_ORDER_TYPE_STOP_LIMIT
    }

    private fun protoExpirationType(expiration: StopOrderExpirationType): ru.tinkoff.piapi.contract.v1.StopOrderExpirationType = when (expiration) {
        StopOrderExpirationType.GOOD_TILL_CANCEL -> ru.tinkoff.piapi.contract.v1.StopOrderExpirationType.STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_CANCEL
        StopOrderExpirationType.GOOD_TILL_DATE -> ru.tinkoff.piapi.contract.v1.StopOrderExpirationType.STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_DATE
    }

    private fun quotationFromDouble(value: Double): Quotation {
        val units = value.toLong()
        val nano = ((value - units) * 1_000_000_000).toInt()
        return Quotation.newBuilder().setUnits(units).setNano(nano).build()
    }

    private fun moneyToDouble(money: MoneyValue?): Double {
        if (money == null) return 0.0
        return money.units + money.nano / 1_000_000_000.0
    }

    private fun parseDate(dateStr: String): com.google.protobuf.Timestamp {
        // Упрощённый парсинг (реализуйте по необходимости)
        return com.google.protobuf.Timestamp.newBuilder().build()
    }

    private fun buildProtoStopOrderRequest(figi: String, req: StopOrderRequest): ru.tinkoff.piapi.contract.v1.PostStopOrderRequest {
        val builder = ru.tinkoff.piapi.contract.v1.PostStopOrderRequest.newBuilder()
            .setFigi(figi)
            .setQuantity(req.quantity)
            .setDirection(protoDirection(req.direction))
            .setAccountId(req.accountId)
            .setStopPrice(quotationFromDouble(req.stopPrice))
            .setStopOrderType(protoStopOrderType(req.stopOrderType))
            .setExpirationType(protoExpirationType(req.expirationType))

        if (req.price != null) builder.setPrice(quotationFromDouble(req.price))
        if (req.expireDate != null) builder.setExpireDate(parseDate(req.expireDate))
        return builder.build()
    }

    override suspend fun postStopOrder(request: StopOrderRequest): String = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val figi = resolveTicker(request.ticker) ?: throw IllegalArgumentException("Тикер ${request.ticker} не найден")

        val builder = PostStopOrderRequest.newBuilder()
            .setFigi(figi)
            .setQuantity(request.quantity)
            .setDirection(protoDirection(request.direction))
            .setAccountId(request.accountId)
            .setStopPrice(quotationFromDouble(request.stopPrice))
            .setStopOrderType(protoStopOrderType(request.stopOrderType))
            .setExpirationType(protoExpirationType(request.expirationType))
        if (request.price != null) builder.setPrice(quotationFromDouble(request.price))
        if (request.expireDate != null) builder.setExpireDate(parseDate(request.expireDate))

        val protoRequest = builder.build()
        val response = if (ServiceLocator.isSandboxMode()) {
            // В песочнице используется специальный метод
            currentApi.sandboxServiceSync.postSandboxStopOrder(protoRequest)
        } else {
            currentApi.stopOrdersServiceSync.postStopOrder(protoRequest)
        }
        response.stopOrderId
    }

    override suspend fun getStopOrders(accountId: String): List<StopOrderUi> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = GetStopOrdersRequest.newBuilder().setAccountId(accountId).build()
        val response = if (ServiceLocator.isSandboxMode()) {
            currentApi.sandboxServiceSync.getSandboxStopOrders(request)
        } else {
            currentApi.stopOrdersServiceSync.getStopOrders(request)
        }

        Log.d(TAG, "StopOrder fields: ${response.allFields}")

        response.stopOrdersList.map { order ->
            StopOrderUi(
                stopOrderId = order.stopOrderId,
                ticker = resolveTicker(order.figi) ?: order.figi,
                figi = order.figi,
                direction = order.direction.name,
                stopPrice = moneyToDouble(order.stopPrice),
                limitPrice = order.price?.let { moneyToDouble(it) },
                quantity = order.lotsRequested,
                type = "unknown",   // временно вместо order.type.name
                status = order.status.name
            )
        }
    }

    override suspend fun cancelStopOrder(accountId: String, stopOrderId: String) {
        withContext(Dispatchers.IO) {
            val currentApi = api ?: throw IllegalStateException("API не инициализирован")
            val request = CancelStopOrderRequest.newBuilder()
                .setAccountId(accountId)
                .setStopOrderId(stopOrderId)
                .build()
            if (ServiceLocator.isSandboxMode()) {
                currentApi.sandboxServiceSync.cancelSandboxStopOrder(request)
            } else {
                currentApi.stopOrdersServiceSync.cancelStopOrder(request)
            }
        }
    }

}