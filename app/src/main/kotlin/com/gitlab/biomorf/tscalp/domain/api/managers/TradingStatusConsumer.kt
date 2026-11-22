package com.gitlab.biomorf.tscalp.domain.api.managers

/**
 * Идентификаторы потребителей trading-statuses в
 * [TradingStatusFlowManager].
 *
 * Каждая ViewModel регистрируется под своим ключом и декларирует
 * интерес к набору `tscalpInstrumentId` по брокеру. Менеджер
 * работает по объединению всех активных интересов.
 *
 * `ORDERS` подключается в T-41 (миграция OrdersViewModel на
 * менеджер); до T-41 ключ не используется — задел, не мёртвый код.
 */
enum class TradingStatusConsumer {
    PORTFOLIO,
    ORDERS,
}
