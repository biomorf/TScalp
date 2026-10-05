package com.example.tscalp.domain.models

/**
 * Идентификатор ошибки конкретного брокера.
 * Используется для расшифровки числовых кодов в человекочитаемые сообщения.
 */
data class BrokerError(
    val brokerName: BrokerName,
    val code: String
)