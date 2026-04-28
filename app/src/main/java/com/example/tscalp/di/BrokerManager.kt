package com.example.tscalp.di

import com.example.tscalp.domain.api.BrokerApi


class BrokerManager(private val brokers: Map<String, BrokerApi>) {

    /**
     * Возвращает брокера по его уникальному имени.
     * @param name имя брокера (например, "TInvest")
     */
    fun getBroker(name: String): BrokerApi? = brokers[name]

    /**
     * Возвращает брокера по умолчанию (Т‑Инвестиции).
     * В будущем можно будет сделать выбор активного брокера в настройках.
     */
    fun getDefaultBroker(): BrokerApi = brokers["TInvest"]
        ?: throw IllegalStateException("Брокер TInvest не зарегистрирован")

    /**
     * Возвращает список имён всех зарегистрированных брокеров.
     * Сейчас – только "TInvest", в будущем пополнится.
     */
    fun getAvailableBrokers(): List<String> = brokers.keys.toList()

    /**
     * Возвращает список всех брокеров (для массовых операций).
     */
    fun getAllBrokers(): List<BrokerApi> = brokers.values.toList()
}