package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.domain.models.*
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.OrderState
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// Архитектурный долг: 19 функций в интерфейсе. См. ROADMAP → «Рефакторинг BrokerApi-реализаций».
@Suppress("TooManyFunctions")
interface BrokerApi {
    val name: BrokerName
    val isInitialized: Boolean

    /**
     * Опциональный контракт unary-снимков (polling в менеджерах).
     *
     * `null` — брокер не поддерживает; менеджер делает `return`
     * с логированием (StreamState остаётся `INITIALIZING` →
     * `STALE`, не `POLLING`).
     */
    val snapshot: BrokerSnapshotApi?

    suspend fun getAccounts(sandboxMode: Boolean): List<BrokerAccount>

    suspend fun openSandboxAccount(): String // возвращает accountId нового счёта

    suspend fun closeSandboxAccount(accountId: String)

    /**
     * Возвращает одноразовый снимок позиций портфеля
     * (в отличие от [subscribePositions], который отдаёт поток).
     *
     * Транспорт зависит от реализации брокера: у T-Invest —
     * gRPC unary ([getPortfolio][ru.tinkoff.piapi.contract.v1.PortfolioResponse]),
     * у BCS / Finam — REST (парсинг JSON).
     *
     * **Полная модель для UI.** Возвращает [PortfolioPosition]
     * (`totalValue`, `profitPercent`, `priceChangePercent` —
     * вычисляемые для карточки). Используется в
     * `InvestRepository` (UI-путь) и как база для
     * [BrokerSnapshotApi.positionsSnapshot] (конвертация в
     * [PositionStreamItem] в оркестраторе).
     *
     * **Не путать** с [BrokerSnapshotApi.positionsSnapshot]:
     * тот partial [PositionStreamItem] (без вычисляемых полей),
     * для merge со stream в `PositionFlowManager`.
     */
    suspend fun fetchPositionsSnapshot(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PortfolioPosition>

    // Единый стрим позиций с автоматическим восстановлением (gRPC → WebSocket → Polling)
    // Если брокер не поддерживает стриминг, реализация может сразу делегировать в polling

    /**
     * Единый поток обновлений позиций.
     * Скрывает внутри себя каскад fallback (gRPC → WebSocket → Polling),
     * а также при подписке сразу эмитит текущий снапшот портфеля.
     */
    fun subscribePositions(accountId: String): Flow<PositionStreamItem>

    /**
     * Стрим последних цен по списку идентификаторов инструментов
     * в формате брокера (для T-Invest — это uid).
     * Реализация по умолчанию возвращает пустой поток — брокеры, не
     * поддерживающие стрим (BCS, Finam), используют её без изменений.
     * TInvest переопределяет.
     */
    fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>> = emptyFlow()

    suspend fun getBalance(accountId: String): Double

    suspend fun sandboxPayIn(
        accountId: String,
        amount: SandboxMoney,
    )

    /**
     * Возвращает специфичный для брокера идентификатор инструмента по тикеру.
     * Для Т-Инвестиций это figi, для БКС — bscticker (пока просто ticker).
     * Может вернуть null, если инструмент не найден.
     */
    suspend fun findInstruments(query: String): List<InstrumentUi>

    /**
     * Выставляет заявку (рыночную или лимитную) через брокера.
     * @param orderType тип заявки (ORDER_TYPE_MARKET или ORDER_TYPE_LIMIT)
     * @param price цена (для рыночной игнорируется, можно передать Quotation.getDefaultInstance())
     */
    suspend fun postOrder(request: BrokerOrderRequest): OrderResult

    /**
     * Выставляет стоп-заявку (take-profit, stop-loss, stop-limit).
     * @return StopOrderResponse с информацией о созданной заявке.
     */
    suspend fun postStopOrder(request: StopOrderRequest): String // возвращает stopOrderId

    /**
     * Возвращает список активных стоп-заявок для указанного счёта.
     */
    suspend fun getStopOrders(accountId: String): List<OrderListItem>

    /**
     * Отменяет стоп-заявку по её идентификатору.
     */
    suspend fun cancelStopOrder(
        accountId: String,
        stopOrderId: String,
    )

    suspend fun getOrders(accountId: String): List<OrderListItem>

    suspend fun cancelOrder(
        accountId: String,
        orderId: String,
    )

    /**
     * Получает последние цены для списка тикеров.
     * Возвращает карту ticker -> цена (или null, если цена недоступна).
     */
    suspend fun getLastPricesByTscalpInstrumentId(ids: List<String>): Map<String, Double?>

    /**
     * Возвращает статусы доступности для торговли для списка идентификаторов инструментов.
     * Ключ — идентификатор инструмента (tscalpInstrumentId), значение — статус доступности.
     */
    suspend fun getTradingStatuses(ids: List<String>): Map<String, TradingAvailability>

    suspend fun subscribeOrderState(accountId: String): Flow<OrderState>

    /**
     * @param tscalpInstrumentId универсальный идентификатор инструмента.
     * @param brokerUid идентификатор инструмента в формате брокера
     *        (для T-Invest равен tscalpInstrumentId; для других брокеров
     *        может отличаться, например figi у BCS).
     */
    suspend fun checkTradeAvailability(
        accountId: String,
        tscalpInstrumentId: String,
        brokerUid: String?,
        direction: OrderDirection,
        quantity: Long,
    ): TradeCheckResult
}
