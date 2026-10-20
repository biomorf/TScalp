package com.gitlab.biomorf.tscalp.domain.models

data class PositionStreamItem(
    // Обязательные: без них позицию нельзя идентифицировать
    val tscalpInstrumentId: String,
    val brokerName: BrokerName,
    val ticker: String,
    val quantity: Long,
    // Опциональные: приходят не всегда
    val currentPrice: Double? = null,
    val averagePositionPrice: Double? = null,
    val expectedYield: Double? = null,
    val classCode: String = "",
    val isin: String = "",
    val pointValue: Double? = null,
    val instrumentType: String = "",
    // Для валютных позиций (instrumentType = "currency").
    // Из WS PositionsStream: money — свободный остаток,
    // blocked — заблокированный. Обе величины — в единицах
    // валюты (для rub — рубли, для других валют — единицы
    // этой валюты).
    // Формула связи money, blocked и quantity пока не
    // подтверждена по документации T-Invest. Возможные
    // варианты (quantity = money, quantity = money + blocked,
    // money и blocked — производные от quantity) проверяются
    // на реальных данных. Поэтому храним раздельно:
    // интерпретация откладывается до UI-задачи, и модель
    // не придётся менять, какая бы формула ни оказалась
    // верной.
    val money: Double? = null,
    val blocked: Double? = null,
    // Код валюты (ISO-4217, например "rub") для валютных
    // позиций (instrumentType = "currency"). Нужен для
    // склейки money/blocked из WS-стрима (там только
    // currency, без uid и ticker) с валютной позицией,
    // пришедшей из polling.
    val currency: String? = null,
)

data class PortfolioPosition(
    // Обязательные: без них позицию нельзя корректно отрисовать
    val tscalpInstrumentId: String,
    val brokerName: BrokerName,
    val name: String,
    val ticker: String,
    val quantity: Long,
    val currentPrice: Double,
    val totalValue: Double,
    // Опциональные: с разумными дефолтами
    val instrumentType: String = "",
    val classCode: String = "",
    val isin: String = "",
    val averagePrice: Double? = null,
    val profit: Double? = null,
    val profitPercent: Double? = null,
    val priceChangePercent: Double? = null,
    val pointValue: Double? = null,
    // Код валюты (ISO-4217) для валютных позиций.
    // Источник — InstrumentUi.currency в fetchPositionsSnapshot.
    // Используется для склейки с WS-стримом (money/blocked).
    val currency: String? = null,
)

/**
 * Прибыль/убыток в процентах от вложенных средств.
 *
 * Формула: yield / (avgPrice × quantity × pointValue) × 100.
 *  - Для акций pointValue = 1 (или null → считаем 1.0),
 *    знаменатель — avgPrice × quantity в рублях.
 *  - Для фьючерсов avgPrice в пунктах, pointValue переводит
 *    вложение в рубли; серверный yield уже в рублях.
 *
 * Возвращает null, если данных недостаточно для расчёта.
 */
fun calculateProfitPercent(
    yield: Double?,
    avgPrice: Double?,
    quantity: Long,
    pointValue: Double?,
): Double? {
    if (yield == null) return null
    if (avgPrice == null || avgPrice <= 0.0) return null
    if (quantity <= 0L) return null
    val point = pointValue?.takeIf { it > 0.0 } ?: 1.0
    val invested = avgPrice * quantity * point
    if (invested <= 0.0) return null
    return yield / invested * 100.0
}

/**
 * Преобразует элемент потока позиций в доменную модель портфельной позиции.
 * profitPercent считается универсально — см. calculateProfitPercent.
 */
fun PositionStreamItem.toPortfolioPosition(): PortfolioPosition {
    val price = currentPrice ?: 0.0
    return PortfolioPosition(
        tscalpInstrumentId = this.tscalpInstrumentId,
        brokerName = brokerName,
        name = ticker,
        ticker = ticker,
        quantity = quantity,
        currentPrice = price,
        averagePrice = averagePositionPrice,
        totalValue = price * quantity,
        profit = expectedYield,
        profitPercent =
            calculateProfitPercent(
                yield = expectedYield,
                avgPrice = averagePositionPrice,
                quantity = quantity,
                pointValue = pointValue,
            ),
        pointValue = pointValue,
        instrumentType = instrumentType,
        isin = isin,
        classCode = classCode,
    )
}
