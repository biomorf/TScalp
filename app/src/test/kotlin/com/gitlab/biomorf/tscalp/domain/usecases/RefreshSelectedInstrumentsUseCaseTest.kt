package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.SharePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshSelectedInstrumentsUseCaseTest {
    private val useCase = RefreshSelectedInstrumentsUseCase()

    private val sber =
        Share(
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    private fun card(
        ticker: String = "SBER",
        accountId: String? = null,
        quantity: Long = 0L,
        averagePrice: Double? = null,
        profit: Double? = null,
        profitPercent: Double? = null,
    ) = SelectedInstrumentInfo(
        instrument = sber,
        currentPrice = null,
        priceChangePercent = null,
        quantity = quantity,
        averagePrice = averagePrice,
        profit = profit,
        profitPercent = profitPercent,
        brokerName = BrokerName.TINVEST,
        accountId = accountId,
    )

    private fun sheet(
        accountId: String,
        quantity: Double = 5.0,
        currentPrice: Double = 150.0,
        profit: Double = 10.0,
    ): PortfolioSheet =
        PortfolioSheet(
            accounts =
                mapOf(
                    AccountKey(BrokerName.TINVEST, accountId) to
                        AccountPortfolio(
                            accountId = accountId,
                            brokerName = BrokerName.TINVEST,
                            positions =
                                listOf(
                                    SharePosition(
                                        instrument = sber,
                                        brokerName = BrokerName.TINVEST,
                                        accountId = accountId,
                                        quantity = quantity,
                                        currentPrice = currentPrice,
                                        averagePrice = 100.0,
                                        profit = profit,
                                    ),
                                ),
                            balances = emptyList(),
                        ),
                ),
        )

    @Test
    fun `empty list returns empty`() {
        val r = useCase.execute(emptyList(), globalAccountId = "acc-1", sheet = sheet("acc-1"))
        assertTrue(r.isEmpty())
    }

    @Test
    fun `global account used when card accountId is null`() {
        val r =
            useCase.execute(
                cards = listOf(card(ticker = "SBER", accountId = null)),
                globalAccountId = "acc-1",
                sheet = sheet("acc-1", quantity = 5.0, currentPrice = 150.0, profit = 10.0),
            )
        assertEquals(1, r.size)
        val enriched = r.single()
        assertEquals(5L, enriched.quantity)
        assertEquals(150.0, enriched.averagePrice!!, 0.0)
        assertEquals(10.0, enriched.profit!!, 0.0)
        assertEquals(2.0, enriched.profitPercent!!, 0.0)
    }

    @Test
    fun `card accountId takes precedence over global`() {
        val r =
            useCase.execute(
                cards = listOf(card(ticker = "SBER", accountId = "acc-2")),
                globalAccountId = "acc-1",
                sheet = sheet("acc-2", quantity = 3.0, currentPrice = 200.0, profit = 30.0),
            )
        assertEquals(1, r.size)
        val enriched = r.single()
        assertEquals(3L, enriched.quantity)
        assertEquals(200.0, enriched.averagePrice!!, 0.0)
        assertEquals(30.0, enriched.profit!!, 0.0)
    }

    @Test
    fun `position not found leaves defaults`() {
        val original = card(ticker = "SBER", accountId = "acc-1", averagePrice = 99.0, profit = 7.0)
        val r =
            useCase.execute(
                cards = listOf(original),
                globalAccountId = null,
                sheet = PortfolioSheet(emptyMap()),
            )
        assertEquals(1, r.size)
        val enriched = r.single()
        assertEquals(0L, enriched.quantity)
        assertEquals(99.0, enriched.averagePrice!!, 0.0)
        assertEquals(0.0, enriched.profit!!, 0.0)
        assertEquals(0.0, enriched.profitPercent!!, 0.0)
    }
}
