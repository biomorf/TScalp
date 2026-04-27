package com.example.tscalp.data.api

import android.util.Log
import com.example.tscalp.di.ServiceLocator
import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.InstrumentUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.*
import ru.ttech.piapi.core.InvestApi
import java.util.concurrent.ConcurrentHashMap
import com.example.tscalp.domain.models.PortfolioPosition

/**
 * Реализация BrokerApi для брокера Т‑Инвестиции (Kotlin SDK).
 * Хранит ticker→figi кэш, самостоятельно управляет своим экземпляром InvestApi.
 */
class TinkoffInvestService : BrokerApi {

    companion object {
        private const val TAG = "TinkoffInvestService"
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
    override suspend fun findInstrumentShorts(query: String): List<InstrumentShort> = withContext(Dispatchers.IO) {
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

    override suspend fun getAccounts(sandboxMode: Boolean): List<Account> = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = GetAccountsRequest.getDefaultInstance()
        return@withContext if (sandboxMode) {
            currentApi.sandboxServiceSync.getSandboxAccounts(request).accountsList
        } else {
            currentApi.usersServiceSync.getAccounts(request).accountsList
        }
    }

    override suspend fun postMarketOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean
    ): PostOrderResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val price = Quotation.newBuilder().setUnits(0).setNano(0).build()
        val request = PostOrderRequest.newBuilder()
            .setFigi(figi)
            .setQuantity(quantity)
            .setPrice(price)
            .setDirection(direction)
            .setAccountId(accountId)
            .setOrderType(OrderType.ORDER_TYPE_MARKET)
            .build()
        return@withContext if (sandboxMode) {
            currentApi.sandboxServiceSync.postSandboxOrder(request)
        } else {
            currentApi.ordersServiceSync.postOrder(request)
        }
    }

    override suspend fun postOrder(
        figi: String,
        quantity: Long,
        direction: OrderDirection,
        accountId: String,
        sandboxMode: Boolean,
        orderType: OrderType,
        price: Quotation
    ): PostOrderResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = PostOrderRequest.newBuilder()
            .setFigi(figi)
            .setQuantity(quantity)
            .setPrice(price)
            .setDirection(direction)
            .setAccountId(accountId)
            .setOrderType(orderType)
            .build()
        return@withContext if (sandboxMode) {
            currentApi.sandboxServiceSync.postSandboxOrder(request)
        } else {
            currentApi.ordersServiceSync.postOrder(request)
        }
    }

    override suspend fun getPortfolio(accountId: String, sandboxMode: Boolean): PortfolioResponse = withContext(Dispatchers.IO) {
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
            // Пытаемся получить тикер из протобуфа, иначе резолвим через figi
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

    override suspend fun getMarginAttributes(accountId: String): GetMarginAttributesResponse = withContext(Dispatchers.IO) {
        val currentApi = api ?: throw IllegalStateException("API не инициализирован")
        val request = GetMarginAttributesRequest.newBuilder().setAccountId(accountId).build()
        currentApi.usersServiceSync.getMarginAttributes(request)
    }

    override suspend fun sandboxPayIn(accountId: String, amount: MoneyValue) {
        withContext(Dispatchers.IO) {
            val currentApi = api ?: throw IllegalStateException("API не инициализирован")
            val request = SandboxPayInRequest.newBuilder()
                .setAccountId(accountId)
                .setAmount(amount)
                .build()
            currentApi.sandboxServiceSync.sandboxPayIn(request)
        }
    }
}