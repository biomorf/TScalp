package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreOrderFormUseCaseTest {
    private val tradingStateRepository = mockk<TradingStateRepository>(relaxed = true)
    private val instrumentRepository = mockk<InstrumentRepository>(relaxed = true)
    private val brokerRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val broker = mockk<BrokerApi>(relaxed = true)

    private val useCase =
        RestoreOrderFormUseCase(
            tradingStateRepository,
            instrumentRepository,
            brokerRegistry,
            logger,
        )

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )
    private val gazp =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-gazp",
            ticker = "GAZP",
            name = "Газпром",
            isin = "RU0007661625",
        )

    private fun stubBrokerReady(ready: Boolean) {
        every { broker.isInitialized } returns ready
        every { brokerRegistry.getDefaultBroker() } returns broker
    }

    @Test
    fun `empty snapshot returns empty form`() =
        runBlocking {
            stubBrokerReady(true)
            coEvery { tradingStateRepository.load() } returns TradingStateSnapshot()

            val r = useCase.execute()

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertNull(data.selectedInstrument)
            assertNull(data.pairedInstrument)
            assertNull(data.currentPointValue)
            assertNull(data.pairedPointValue)
            assertEquals(false, data.pairTradingEnabled)
            assertEquals("", data.quantity)
            assertEquals("10", data.pairedMultiplier)
            assertEquals(OrderTypeSelection.Market, data.orderType)
            coVerify(exactly = 0) { instrumentRepository.getInstrument(any()) }
        }

    @Test
    fun `only selected resolves and computes pointValue`() =
        runBlocking {
            stubBrokerReady(true)
            coEvery { tradingStateRepository.load() } returns
                TradingStateSnapshot(selectedTscalpInstrumentId = "uid-sber")
            coEvery { instrumentRepository.getInstrument("uid-sber") } returns sber

            val r = useCase.execute()

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(sber, data.selectedInstrument)
            assertNull(data.pairedInstrument)
            assertEquals(sber.moneyPerPointOfPrice, data.currentPointValue!!, 0.0)
            assertNull(data.pairedPointValue)
        }

    @Test
    fun `both selected resolves and computes both pointValues`() =
        runBlocking {
            stubBrokerReady(true)
            coEvery { tradingStateRepository.load() } returns
                TradingStateSnapshot(
                    selectedTscalpInstrumentId = "uid-sber",
                    pairedTscalpInstrumentId = "uid-gazp",
                    pairTradingEnabled = true,
                    quantity = "3",
                    pairedMultiplier = "5",
                    orderType = OrderTypeSelection.Limit,
                )
            coEvery { instrumentRepository.getInstrument("uid-sber") } returns sber
            coEvery { instrumentRepository.getInstrument("uid-gazp") } returns gazp

            val r = useCase.execute()

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertEquals(sber, data.selectedInstrument)
            assertEquals(gazp, data.pairedInstrument)
            assertEquals(true, data.pairTradingEnabled)
            assertEquals("3", data.quantity)
            assertEquals("5", data.pairedMultiplier)
            assertEquals(OrderTypeSelection.Limit, data.orderType)
            assertEquals(sber.moneyPerPointOfPrice, data.currentPointValue!!, 0.0)
            assertEquals(gazp.moneyPerPointOfPrice, data.pairedPointValue!!, 0.0)
        }

    @Test
    fun `instrument not found leaves field null without failure`() =
        runBlocking {
            stubBrokerReady(true)
            coEvery { tradingStateRepository.load() } returns
                TradingStateSnapshot(selectedTscalpInstrumentId = "uid-missing")
            coEvery { instrumentRepository.getInstrument("uid-missing") } returns null

            val r = useCase.execute()

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertNull(data.selectedInstrument)
            assertNull(data.currentPointValue)
        }

    @Test
    fun `broker not ready skips getInstrument but keeps snapshot`() =
        runBlocking {
            stubBrokerReady(false)
            coEvery { tradingStateRepository.load() } returns
                TradingStateSnapshot(
                    selectedTscalpInstrumentId = "uid-sber",
                    pairedTscalpInstrumentId = "uid-gazp",
                    pairTradingEnabled = true,
                    quantity = "7",
                    pairedMultiplier = "2",
                    orderType = OrderTypeSelection.StopLoss,
                )

            val r = useCase.execute()

            assertTrue(r is AppResult.Success)
            val data = (r as AppResult.Success).data
            assertNull(data.selectedInstrument)
            assertNull(data.pairedInstrument)
            assertEquals(true, data.pairTradingEnabled)
            assertEquals("7", data.quantity)
            assertEquals("2", data.pairedMultiplier)
            assertEquals(OrderTypeSelection.StopLoss, data.orderType)
            coVerify(exactly = 0) { instrumentRepository.getInstrument(any()) }
        }
}
