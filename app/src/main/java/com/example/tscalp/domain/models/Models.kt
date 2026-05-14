package com.example.tscalp.domain.models

data class PositionStreamItem(
    val instrumentUid: String,
    val ticker: String,
    val quantity: Long,
    val currentPrice: Double?,       // текущая цена (может быть null)
    val averagePositionPrice: Double?, // средняя цена позиции
    val expectedYield: Double?,       // ожидаемая доходность (абсолютная)
    val pointValue: Double? = null,
    val instrumentType: String = ""
)

data class PortfolioPosition(
    val tscalpInstrumentId: String = "",
    val brokerName: String = "",
    val instrumentType: String = "",
    val name: String,
    val ticker: String,
    val quantity: Long,
    val currentPrice: Double,
    val averagePrice: Double? = null,
    val totalValue: Double,
    val profit: Double?,
    val profitPercent: Double?,
    val priceChangePercent: Double? = null,
    val pointValue: Double? = null      // ← стоимость пункта для фьючерсов
)

/**
 * Универсальное представление денежной суммы для пополнения песочницы.
 * Не зависит от protobuf.
 */
data class SandboxMoney(
    val currency: String,
    val units: Long,
    val nano: Int = 0
)
