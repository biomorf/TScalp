package com.example.tscalp.di

import com.example.tscalp.domain.api.BrokerApi

class BrokerManager(private val brokers: Map<String, BrokerApi>) {

    /**
     * Возвращает брокера по его уникальному имени.
     * @param name имя брокера (например, "tinkoff")
     */
    fun getBroker(name: String): BrokerApi? = brokers[name]

    /**
     * Возвращает брокера по умолчанию (Т‑Инвестиции).
     * В будущем можно будет сделать выбор активного брокера в настройках.
     */
    fun getDefaultBroker(): BrokerApi = brokers["tinkoff"]
        ?: throw IllegalStateException("Брокер Tinkoff не зарегистрирован")

    /**
     * Возвращает список имён всех зарегистрированных брокеров.
     * Сейчас – только "tinkoff", в будущем пополнится.
     */
    fun getAvailableBrokers(): List<String> = brokers.keys.toList()

    /**
     * Возвращает список всех брокеров (для массовых операций).
     */
    fun getAllBrokers(): List<BrokerApi> = brokers.values.toList()
}