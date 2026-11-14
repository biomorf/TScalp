package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PriceUpdate
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.usecases.SelectInstrumentResult
import com.gitlab.biomorf.tscalp.domain.usecases.SelectInstrumentUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectPairedInstrumentUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectPairedResult
import com.gitlab.biomorf.tscalp.domain.usecases.SyncPriceInterestUseCase
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderInstrumentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val selectInstrumentUseCase = mockk<SelectInstrumentUseCase>(relaxed = true)
    private val selectPairedInstrumentUseCase = mockk<SelectPairedInstrumentUseCase>(relaxed = true)
    private val syncPriceInterestUseCase = mockk<SyncPriceInterestUseCase>(relaxed = true)
    private val priceFlowManager = mockk<PriceFlowManager>(relaxed = true)
    private val mediator = mockk<OrderFormMediator>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "\u0421\u0431\u0435\u0440\u0431\u0430\u043d\u043a",
            isin = "RU0009029540",
        )

    private val tmon =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-tmon",
            ticker = "TMON",
            name = "\u0422\u041c\u041e\u041d",
            isin = "RU0000000000",
        )

    private fun stubDefaults() {
        every { priceFlowManager.prices } returns MutableSharedFlow()
    }

    private fun viewModel(): OrderInstrumentViewModel {
        stubDefaults()
        return OrderInstrumentViewModel(
            selectInstrumentUseCase,
            selectPairedInstrumentUseCase,
            syncPriceInterestUseCase,
            priceFlowManager,
            mediator,
            logger,
        )
    }

    private fun info(
        instrument: Share,
        accountId: String?,
    ) = SelectedInstrumentInfo(
        instrument = instrument,
        currentPrice = 150.0,
        priceChangePercent = null,
        quantity = 0L,
        averagePrice = null,
        profit = null,
        profitPercent = null,
        brokerName = instrument.brokerName,
        accountId = accountId,
    )

    @Test
    fun `onInstrumentSelected success updates state`() =
        runTest {
            coEvery { selectInstrumentUseCase.execute(sber, null) } returns
                AppResult.Success(SelectInstrumentResult(card = info(sber, "acc-1"), pointValue = 1.0))
            val vm = viewModel()

            vm.onInstrumentSelected(sber)
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(sber, s.selectedInstrument)
            assertEquals("SBER", s.ticker)
            assertEquals(BrokerName.TINVEST, s.selectedBroker)
            assertEquals(150.0, s.currentPrice ?: 0.0, 0.001)
            assertEquals(1.0, s.currentPointValue ?: 0.0, 0.001)
            assertEquals("acc-1", s.selectedAccountId)
            assertTrue(!s.isPriceLoading)
        }

    @Test
    fun `onInstrumentSelected failure keeps state and resets loading`() =
        runTest {
            coEvery { selectInstrumentUseCase.execute(sber, null) } returns
                AppResult.Failure(AppError.Unknown("fail", null))
            val vm = viewModel()

            vm.onInstrumentSelected(sber)
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(sber, s.selectedInstrument)
            assertTrue(!s.isPriceLoading)
            assertNull(s.selectedAccountId)
        }

    @Test
    fun `clearSelectedInstrument resets primary fields`() =
        runTest {
            coEvery { selectInstrumentUseCase.execute(sber, null) } returns
                AppResult.Success(SelectInstrumentResult(card = info(sber, "acc-1"), pointValue = 1.0))
            val vm = viewModel()
            vm.onInstrumentSelected(sber)
            advanceUntilIdle()

            vm.clearSelectedInstrument()

            val s = vm.state.value
            assertNull(s.selectedInstrument)
            assertEquals("", s.ticker)
            assertEquals(BrokerName.TINVEST, s.selectedBroker)
            assertNull(s.selectedAccountId)
            assertNull(s.currentPrice)
            assertTrue(!s.isPriceLoading)
        }

    @Test
    fun `onPairedInstrumentSelected success updates paired state`() =
        runTest {
            coEvery { selectPairedInstrumentUseCase.execute(tmon) } returns
                AppResult.Success(SelectPairedResult(price = 75.0, pointValue = 1.0))
            val vm = viewModel()

            vm.onPairedInstrumentSelected(tmon)
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(tmon, s.pairedInstrument)
            assertEquals(BrokerName.TINVEST, s.pairedBroker)
            assertNull(s.pairedAccountId)
            assertEquals(75.0, s.pairCurrentPrice ?: 0.0, 0.001)
        }

    @Test
    fun `clearPairedInstrument resets paired fields`() =
        runTest {
            coEvery { selectPairedInstrumentUseCase.execute(tmon) } returns
                AppResult.Success(SelectPairedResult(price = 75.0, pointValue = 1.0))
            val vm = viewModel()
            vm.onPairedInstrumentSelected(tmon)
            advanceUntilIdle()

            vm.clearPairedInstrument()

            val s = vm.state.value
            assertNull(s.pairedInstrument)
            assertEquals(BrokerName.TINVEST, s.pairedBroker)
            assertNull(s.pairedAccountId)
        }

    @Test
    fun `onAccountSaved updates selectedAccountId`() =
        runTest {
            val vm = viewModel()
            vm.onAccountSaved("acc-new")
            assertEquals("acc-new", vm.state.value.selectedAccountId)
        }

    @Test
    fun `onPairedAccountSaved updates pairedAccountId`() =
        runTest {
            val vm = viewModel()
            vm.onPairedAccountSaved("paired-acc")
            assertEquals("paired-acc", vm.state.value.pairedAccountId)
        }

    @Test
    fun `updateInstrumentPrice primary and paired slots`() =
        runTest {
            coEvery { selectInstrumentUseCase.execute(sber, null) } returns
                AppResult.Success(SelectInstrumentResult(card = info(sber, "acc-1"), pointValue = 1.0))
            coEvery { selectPairedInstrumentUseCase.execute(tmon) } returns
                AppResult.Success(SelectPairedResult(price = 75.0, pointValue = 1.0))
            val prices = MutableSharedFlow<PriceUpdate>()
            every { priceFlowManager.prices } returns prices

            val vm =
                OrderInstrumentViewModel(
                    selectInstrumentUseCase,
                    selectPairedInstrumentUseCase,
                    syncPriceInterestUseCase,
                    priceFlowManager,
                    mediator,
                    logger,
                )
            vm.onInstrumentSelected(sber)
            advanceUntilIdle()
            vm.onPairedInstrumentSelected(tmon)
            advanceUntilIdle()

            // primary update
            prices.emit(PriceUpdate(BrokerName.TINVEST, "uid-sber", 200.0))
            advanceUntilIdle()
            val s1 = vm.state.value
            assertEquals(200.0, s1.currentPrice ?: 0.0, 0.001)

            // paired update
            prices.emit(PriceUpdate(BrokerName.TINVEST, "uid-tmon", 80.0))
            advanceUntilIdle()
            val s2 = vm.state.value
            assertEquals(80.0, s2.pairCurrentPrice ?: 0.0, 0.001)
        }
}
