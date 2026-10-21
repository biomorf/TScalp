package com.gitlab.biomorf.tscalp.data.api

import ru.ttech.piapi.core.InvestApi

/**
 * Разделяемое состояние T-Invest API.
 *
 * Держит ссылки на InvestApi, набор gRPC-каналов и текущий режим (sandbox/prod).
 * Используется фасадом TInvestBrokerAPI и сервисами market-data / orders.
 *
 * Все поля @Volatile: инициализация происходит в одном потоке (settings),
 * а чтение — из корутин IO во время работы приложения.
 */
class TInvestApiState {
    @Volatile var api: InvestApi? = null

    @Volatile var channels: TInvestChannelFactory.Channels? = null

    /** Токен доступа. Нужен REST-транспорту для заголовка Authorization. */
    @Volatile var token: String? = null

    @Volatile var sandboxMode: Boolean = true
}
