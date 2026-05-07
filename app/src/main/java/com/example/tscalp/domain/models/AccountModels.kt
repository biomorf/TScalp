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

// Расширение для удобного отображения в UI
val BrokerAccountType.displayName: String
    get() = when (this) {
        BrokerAccountType.BROKER -> "Брокерский"
        BrokerAccountType.IIS -> "ИИС"
        BrokerAccountType.INVEST_BOX -> "Invest Box"
        BrokerAccountType.OTHER -> "Другой"
    }

