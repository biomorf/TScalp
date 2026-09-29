package com.example.tscalp.di

import com.example.tscalp.domain.api.BrokerApi
import com.example.tscalp.domain.models.BrokerName


class BrokerManager(private val brokers: Map<BrokerName, BrokerApi>) {

    /**
     * Возвращает брокера по имени. Типобезопасный вариант.
     */
    fun getBroker(name: BrokerName): BrokerApi? = brokers[name]

    /**
     * Возвращает брокера по строковому ключу.
     * Оставлен для плавной миграции — постепенно заменяется на enum-версию.
     */
    fun getBroker(name: String): BrokerApi? =
        BrokerName.fromKey(name)?.let { brokers[it] }

    /**
     * Брокер по умолчанию (Т‑Инвестиции).
     */
    fun getDefaultBroker(): BrokerApi = brokers[BrokerName.TINVEST]
        ?: throw IllegalStateException("Брокер TInvest не зарегистрирован")


    /**
     * Список ключей брокеров — для отображения в UI.
     */
    fun getAvailableBrokers(): List<String> = brokers.keys.map { it.key }

    /**
     * Возвращает список всех брокеров (для массовых операций).
     */
    fun getAllBrokers(): List<BrokerApi> = brokers.values.toList()
}