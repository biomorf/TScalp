package com.gitlab.biomorf.tscalp.domain.models

data class PositionStreamItem(
    // Обязательные: без них позицию нельзя идентифицировать
    val instrumentUid: String,
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
    val instrumentType: String = ""
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
    val pointValue: Double? = null
)

/**
 * Преобразует элемент потока позиций в доменную модель портфельной позиции.
 *
 * Заполняет все поля, кроме profitPercent: он передаётся вызывающей стороной,
 * потому что формула отличается для разных потребителей
 * (PortfolioViewModel считает от разницы цен, OrdersViewModel — от абсолютного дохода).
 * Унификация формулы — отдельная задача (см. roadmap).
 */
fun PositionStreamItem.toPortfolioPosition(profitPercent: Double? = null): PortfolioPosition {
    val price = currentPrice ?: 0.0
    return PortfolioPosition(
        tscalpInstrumentId = instrumentUid,
        brokerName = brokerName,
        name = ticker,
        ticker = ticker,
        quantity = quantity,
        currentPrice = price,
        averagePrice = averagePositionPrice,
        totalValue = price * quantity,
        profit = expectedYield,
        profitPercent = profitPercent,
        pointValue = pointValue,
        instrumentType = instrumentType,
        isin = isin,
        classCode = classCode
    )
}
