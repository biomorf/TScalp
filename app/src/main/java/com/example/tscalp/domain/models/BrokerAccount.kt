package com.example.tscalp.domain.models

/**
 * Универсальный тип счёта, не зависящий от protobuf.
 */
enum class BrokerAccountType { BROKER, IIS, INVEST_BOX, OTHER }

/**
 * Универсальная модель брокерского счёта для использования в интерфейсе BrokerApi.
 */
data class BrokerAccount(
    val id: String,
    val name: String,
    val type: BrokerAccountType
)