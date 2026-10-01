package com.example.tscalp.di

import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.BrokerName


class BrokerManager(private val brokers: Map<BrokerName, BrokerApi>) {

    /**
     * Возвращает брокера по имени. Типобезопасный вариант.
     */
    fun getBroker(name: BrokerName): BrokerApi? = brokers[name]

    /**
     * Брокер по умолчанию (Т‑Инвестиции).
     */
    fun getDefaultBroker(): BrokerApi = brokers[BrokerName.TINVEST]
        ?: throw IllegalStateException("Брокер TInvest не зарегистрирован")

    /**
     * Возвращает список ключей брокеров в типизированном виде — для UI и pager'а.
     * Заменяет строковый [getAvailableBrokers] в новых вызовах.
     */
    fun getAvailableBrokerNames(): List<BrokerName> = brokers.keys.toList()

    /**
     * Возвращает список всех брокеров (для массовых операций).
     */
    fun getAllBrokers(): List<BrokerApi> = brokers.values.toList()
}