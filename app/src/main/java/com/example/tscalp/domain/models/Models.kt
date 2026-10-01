package com.example.tscalp.domain.models

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
 * Универсальное представление денежной суммы для пополнения песочницы.
 * Не зависит от protobuf.
 */
data class SandboxMoney(
    val currency: String,
    val units: Long,
    val nano: Int = 0
)
