package com.gitlab.biomorf.tscalp.domain.models

/**
 * Обновление цены от PriceStreamManager.
 *
 * Содержит брокера, потому что `tscalpInstrumentId` уникален
 * только внутри брокера. Пока активен TInvest, но структура
 * готова к мультиброкерности: при появлении второго источника
 * consumer'ы смогут фильтровать по brokerName.
 */
data class PriceUpdate(
    val brokerName: BrokerName,
    val tscalpInstrumentId: String,
    val price: Double
)