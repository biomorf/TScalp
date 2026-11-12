package com.gitlab.biomorf.tscalp.testutil

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BalanceApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BcsLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerInstrumentApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.api.roles.FinamLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.MarketDataApi
import com.gitlab.biomorf.tscalp.domain.api.roles.OrdersApi
import com.gitlab.biomorf.tscalp.domain.api.roles.PortfolioApi
import com.gitlab.biomorf.tscalp.domain.api.roles.SandboxApi
import com.gitlab.biomorf.tscalp.domain.api.roles.StreamsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TInvestLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TradeCheckApi
import com.gitlab.biomorf.tscalp.domain.api.roles.UserInstrumentsApi
import com.gitlab.biomorf.tscalp.domain.models.BrokerName

/**
 * Минимальная fake-реализация [BrokerApi] для Hilt-тестов.
 *
 * Все роли — `null` (не поддерживает). Только `name` и
 * `isInitialized`; последний — `override var`, чтобы
 * [FakeBrokerRegistry.setAnyInitialized] мог переключать его
 * синхронно с [FakeBrokerRegistry.anyInitialized]. Это важно для
 * `CheckBrokerInitializationUseCase`, который вычисляет
 * `getAllBrokers().any { it.isInitialized }` (не читает
 * `anyInitialized`).
 */
class FakeBrokerApi(
    override val name: BrokerName,
    initialized: Boolean = false,
) : BrokerApi {
    override var isInitialized: Boolean = initialized

    override val snapshot: BrokerSnapshotApi? = null
    override val streams: StreamsApi? = null
    override val lifecycleTInvest: TInvestLifecycleApi? = null
    override val lifecycleBcs: BcsLifecycleApi? = null
    override val lifecycleFinam: FinamLifecycleApi? = null
    override val instrument: BrokerInstrumentApi? = null
    override val orders: OrdersApi? = null
    override val accounts: AccountsApi? = null
    override val sandbox: SandboxApi? = null
    override val userInstruments: UserInstrumentsApi? = null
    override val balance: BalanceApi? = null
    override val portfolio: PortfolioApi? = null
    override val marketData: MarketDataApi? = null
    override val tradeCheck: TradeCheckApi? = null
}
