package com.example.tscalp.di

import com.example.tscalp.domain.api.BrokerApi

class BrokerManager(private val brokers: Map<String, BrokerApi>) {
    fun getBroker(name: String): BrokerApi? = brokers[name]
    fun getDefaultBroker(): BrokerApi = brokers["tinkoff"] ?: throw IllegalStateException("Брокер Tinkoff не зарегистрирован")
    fun getAllBrokers(): List<BrokerApi> = brokers.values.toList()
}