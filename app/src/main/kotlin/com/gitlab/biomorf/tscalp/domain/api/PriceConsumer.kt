package com.gitlab.biomorf.tscalp.domain.api

/**
 * Идентификаторы потребителей рыночных цен в PriceStreamManager.
 *
 * Каждая ViewModel регистрируется под своим ключом и декларирует
 * интерес к набору инструментов. Менеджер работает по объединению
 * всех активных интересов.
 */
enum class PriceConsumer {
    PORTFOLIO,
    ORDERS
}