package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.PortfolioSheet
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.SharePosition
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SelectInstrumentUseCaseTest {
    private val instrumentRepository = mockk<InstrumentRepository>(relaxed = true)
    private val marketDataRepository = mockk<MarketDataRepository>(relaxed = true)
    private val positionFlowManager = mockk<PositionFlowManager>(relaxed = true)
    private val cardsRepository = mockk<CardsRepository>(relaxed = true)
    private val accountSelectionRepository = mockk<AccountSelectionRepository>(relaxed = true)
    private val tradingStateRepository = mockk<TradingStateRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val useCase =
        SelectInstrumentUseCase(
            instrumentRepository = instrumentRepository,
            marketDataRepository = marketDataRepository,
            positionFlowManager = positionFlowManager,
            cardsRepository = cardsRepository,
            accountSelectionRepository = accountSelectionRepository,
            tradingStateRepository = tradingStateRepository,
            logger = logger,
        )

    private val inputInstrument =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-1",
            ticker = "SBER",
            name = "Sberbank",
            isin = "RU0009029540",
        )

    private val actualInstrument =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-1",
            ticker = "SBER",
            name = "Sberbank",
            isin = "RU0009029540",
            priceStepCost = 10.0,
            minPriceIncrement = 2.0,
        )

    private fun stubDefaults() {
        coEvery { instrumentRepository.getInstrument("uid-1") } returns actualInstrument
        coEvery { marketDataRepository.getLastPricesResult(listOf("uid-1")) } returns
            AppResult.Success(mapOf("uid-1" to 100.0))
        every { accountSelectionRepository.selected(BrokerName.TINVEST) } returns flowOf("acc-1")
        every { positionFlowManager.sheet } returns MutableStateFlow(PortfolioSheet(emptyMap()))
        every { cardsRepository.identities } returns flowOf(emptyList())
        every { tradingStateRepository.state } returns
            MutableStateFlow(TradingStateSnapshot(quantity = 1L))
    }

    @Test
    fun `success returns enriched card and pointValue`() =
        runBlocking {
            stubDefaults()
            val position =
                SharePosition(
                    instrument = actualInstrument,
                    brokerName = BrokerName.TINVEST,
                    accountId = "acc-1",
                    quantity = 5.0,
                    currentPrice = 100.0,
                    averagePrice = 95.0,
                    profit = 25.0,
                )
            every { positionFlowManager.sheet } returns
                MutableStateFlow(
                    PortfolioSheet(
                        mapOf(
                            AccountKey(BrokerName.TINVEST, "acc-1") to
                                AccountPortfolio(
                                    accountId = "acc-1",
                                    brokerName = BrokerName.TINVEST,
                                    positions = listOf(position),
                                    balances = emptyList(),
                                ),
                        ),
                    ),
                )

            val r = useCase.execute(inputInstrument)

            assertTrue(r is AppResult.Success)
            val result = (r as AppResult.Success<SelectInstrumentResult>).data
            assertEquals(5L, result.card.quantity)
            assertEquals(100.0, result.card.currentPrice ?: -1.0, 0.001)
            assertEquals(100.0, result.card.averagePrice ?: -1.0, 0.001)
            assertEquals(25.0, result.card.profit ?: -1.0, 0.001)
            assertEquals(BrokerName.TINVEST, result.card.brokerName)
            assertNull(result.card.accountId)
            assertEquals(5.0, result.pointValue, 0.001)
            coVerify(exactly = 1) {
                tradingStateRepository.save(
                    match { it.selectedTscalpInstrumentId == "uid-1" },
                )
            }
        }

    @Test
    fun `getInstrument null falls back to input instrument pointValue`() =
        runBlocking {
            stubDefaults()
            coEvery { instrumentRepository.getInstrument("uid-1") } returns null

            val r = useCase.execute(inputInstrument)

            assertTrue(r is AppResult.Success)
            val result = (r as AppResult.Success<SelectInstrumentResult>).data
            assertEquals(1.0, result.pointValue, 0.001)
        }

    @Test
    fun `getLastPrices failure returns null price without error`() =
        runBlocking {
            stubDefaults()
            coEvery { marketDataRepository.getLastPricesResult(listOf("uid-1")) } returns
                AppResult.Failure(AppError.Network("network down", null))

            val r = useCase.execute(inputInstrument)

            assertTrue(r is AppResult.Success)
            val result = (r as AppResult.Success<SelectInstrumentResult>).data
            assertNull(result.card.currentPrice)
            verify(atLeast = 1) { logger.w(any(), any(), any()) }
        }

    @Test
    fun `null accountId skips portfolio lookup and quantity is 0`() =
        runBlocking {
            stubDefaults()
            every { accountSelectionRepository.selected(BrokerName.TINVEST) } returns flowOf(null)

            val r = useCase.execute(inputInstrument)

            assertTrue(r is AppResult.Success)
            val result = (r as AppResult.Success<SelectInstrumentResult>).data
            assertEquals(0L, result.card.quantity)
            assertNull(result.card.averagePrice)
            assertNull(result.card.profit)
        }

    @Test
    fun `same-broker card preserves accountId, brokerName comes from instrument`() =
        runBlocking {
            stubDefaults()
            every { cardsRepository.identities } returns
                flowOf(
                    listOf(
                        CardIdentity(
                            tscalpInstrumentId = "uid-1",
                            brokerName = BrokerName.TINVEST,
                            accountId = "t-inv",
                        ),
                    ),
                )

            val r = useCase.execute(inputInstrument, currentAccountId = "t-inv")

            assertTrue(r is AppResult.Success)
            val result = (r as AppResult.Success<SelectInstrumentResult>).data
            assertEquals(BrokerName.TINVEST, result.card.brokerName)
            assertEquals("t-inv", result.card.accountId)
        }

    @Test
    fun `card for different broker is ignored`() =
        runBlocking {
            stubDefaults()
            every { cardsRepository.identities } returns
                flowOf(
                    listOf(
                        CardIdentity(
                            tscalpInstrumentId = "uid-1",
                            brokerName = BrokerName.BCS,
                            accountId = "bcs-acc",
                        ),
                    ),
                )

            val r = useCase.execute(inputInstrument)

            assertTrue(r is AppResult.Success)
            val result = (r as AppResult.Success<SelectInstrumentResult>).data
            assertEquals(BrokerName.TINVEST, result.card.brokerName)
            assertNull(result.card.accountId)
        }

    @Test
    fun `null tradingState loads from repository`() =
        runBlocking {
            stubDefaults()
            every { tradingStateRepository.state } returns MutableStateFlow<TradingStateSnapshot?>(null)
            coEvery { tradingStateRepository.load() } returns TradingStateSnapshot(quantity = 7L)

            useCase.execute(inputInstrument)

            coVerify(exactly = 1) { tradingStateRepository.load() }
        }

    @Test
    fun `exception returns Failure Unknown`() =
        runBlocking {
            stubDefaults()
            coEvery { instrumentRepository.getInstrument("uid-1") } throws RuntimeException("boom")

            val r = useCase.execute(inputInstrument)

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
        }

    @Test
    fun `cancellation rethrows`() =
        runBlocking {
            stubDefaults()
            coEvery { instrumentRepository.getInstrument("uid-1") } throws CancellationException()

            try {
                useCase.execute(inputInstrument)
                fail("expected CancellationException")
            } catch (expected: CancellationException) {
                // OK
            }
        }

    @Test
    fun `existing card accountId takes priority over global for position lookup`() =
        runBlocking {
            stubDefaults()
            every { accountSelectionRepository.selected(BrokerName.TINVEST) } returns
                flowOf("global-acc")
            every { cardsRepository.identities } returns
                flowOf(
                    listOf(
                        CardIdentity(
                            tscalpInstrumentId = "uid-1",
                            brokerName = BrokerName.TINVEST,
                            accountId = "card-acc",
                        ),
                    ),
                )
            val posGlobal =
                SharePosition(
                    instrument = actualInstrument,
                    brokerName = BrokerName.TINVEST,
                    accountId = "global-acc",
                    quantity = 10.0,
                    currentPrice = 100.0,
                )
            val posCard =
                SharePosition(
                    instrument = actualInstrument,
                    brokerName = BrokerName.TINVEST,
                    accountId = "card-acc",
                    quantity = 3.0,
                    currentPrice = 100.0,
                )
            every { positionFlowManager.sheet } returns
                MutableStateFlow(
                    PortfolioSheet(
                        mapOf(
                            AccountKey(BrokerName.TINVEST, "global-acc") to
                                AccountPortfolio(
                                    accountId = "global-acc",
                                    brokerName = BrokerName.TINVEST,
                                    positions = listOf(posGlobal),
                                    balances = emptyList(),
                                ),
                            AccountKey(BrokerName.TINVEST, "card-acc") to
                                AccountPortfolio(
                                    accountId = "card-acc",
                                    brokerName = BrokerName.TINVEST,
                                    positions = listOf(posCard),
                                    balances = emptyList(),
                                ),
                        ),
                    ),
                )

            val r = useCase.execute(inputInstrument, currentAccountId = "card-acc")

            assertTrue(r is AppResult.Success)
            val result = (r as AppResult.Success<SelectInstrumentResult>).data
            assertEquals(3L, result.card.quantity)
            assertEquals("card-acc", result.card.accountId)
        }

    @Test
    fun `findCard by composite uses currentAccountId`() =
        runBlocking {
            stubDefaults()
            val cardA = CardIdentity("uid-1", BrokerName.TINVEST, "A")
            val cardB = CardIdentity("uid-1", BrokerName.TINVEST, "B")
            every { cardsRepository.identities } returns flowOf(listOf(cardA, cardB))

            val r = useCase.execute(inputInstrument, currentAccountId = "B")

            assertTrue(r is AppResult.Success)
            val result = (r as AppResult.Success<SelectInstrumentResult>).data
            assertEquals("B", result.card.accountId)
        }

    @Test
    fun `persistIdentity deduplicates only target composite`() =
        runBlocking {
            stubDefaults()
            val cardA = CardIdentity("uid-1", BrokerName.TINVEST, "A")
            val cardB = CardIdentity("uid-1", BrokerName.TINVEST, "B")
            every { cardsRepository.identities } returns flowOf(listOf(cardA, cardB))

            useCase.execute(inputInstrument, currentAccountId = "A")

            coVerify(exactly = 1) {
                cardsRepository.save(
                    match { cards ->
                        cards.size == 2 &&
                            cards.any { it.tscalpInstrumentId == "uid-1" && it.brokerName == BrokerName.TINVEST && it.accountId == "A" } &&
                            cards.any { it.tscalpInstrumentId == "uid-1" && it.brokerName == BrokerName.TINVEST && it.accountId == "B" }
                    },
                )
            }
        }
}
