package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.usecases.CalculatePairExecUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.CalculateTradeDetailsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.PrimaryOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.RestoreOrderFormUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.RestoredFormState
import com.gitlab.biomorf.tscalp.domain.usecases.SaveFormStateUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectInstrumentResult
import com.gitlab.biomorf.tscalp.domain.usecases.SelectInstrumentUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectPairedInstrumentUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.SelectPairedResult
import com.gitlab.biomorf.tscalp.domain.usecases.SyncPriceInterestUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.TradeDetails
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderFormViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val calculateTradeDetails = mockk<CalculateTradeDetailsUseCase>(relaxed = true)
    private val calculatePairExecUseCase = mockk<CalculatePairExecUseCase>(relaxed = true)
    private val saveFormStateUseCase = mockk<SaveFormStateUseCase>(relaxed = true)
    private val tradingPreferencesRepository = mockk<TradingPreferencesRepository>(relaxed = true)
    private val priceFlowManager = mockk<PriceFlowManager>(relaxed = true)
    private val syncPriceInterestUseCase = mockk<SyncPriceInterestUseCase>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val placeOrderUseCase = mockk<PlaceOrderUseCase>(relaxed = true)
    private val restoreOrderFormUseCase = mockk<RestoreOrderFormUseCase>(relaxed = true)
    private val selectInstrumentUseCase = mockk<SelectInstrumentUseCase>(relaxed = true)
    private val selectPairedInstrumentUseCase = mockk<SelectPairedInstrumentUseCase>(relaxed = true)

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    private val tmon =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-tmon",
            ticker = "TMON",
            name = "ТМОН",
            isin = "RU0000000000",
        )

    private fun stubDefaults() {
        every { tradingPreferencesRepository.confirmOrdersEnabledFlow() } returns flowOf(true)
        every { priceFlowManager.prices } returns MutableSharedFlow()
        coEvery { restoreOrderFormUseCase.execute() } returns
            AppResult.Failure(AppError.Unknown("not initialized", null))
    }

    private fun createVm(): OrderFormViewModel {
        stubDefaults()
        return OrderFormViewModel(
            calculateTradeDetails,
            calculatePairExecUseCase,
            saveFormStateUseCase,
            tradingPreferencesRepository,
            priceFlowManager,
            syncPriceInterestUseCase,
            logger,
            placeOrderUseCase,
            restoreOrderFormUseCase,
            selectInstrumentUseCase,
            selectPairedInstrumentUseCase,
        )
    }

    private fun info(
        instrument: Share,
        accountId: String?,
    ) = SelectedInstrumentInfo(
        instrument = instrument,
        currentPrice = 100.0,
        priceChangePercent = null,
        quantity = 0L,
        averagePrice = null,
        profit = null,
        profitPercent = null,
        brokerName = instrument.brokerName,
        accountId = accountId,
    )

    @Test
    fun `onInstrumentSelected sets selectedBroker and selectedAccountId`() =
        runTest {
            coEvery { selectInstrumentUseCase.execute(sber, null) } returns
                AppResult.Success(SelectInstrumentResult(card = info(sber, "A"), pointValue = 1.0))

            val vm = createVm()
            vm.onInstrumentSelected(sber)
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(BrokerName.TINVEST, s.selectedBroker)
            assertEquals("A", s.selectedAccountId)
            assertEquals(sber, s.selectedInstrument)
        }

    @Test
    fun `onInstrumentSelected passes currentAccountId to usecase`() =
        runTest {
            coEvery { selectInstrumentUseCase.execute(sber, "old-A") } returns
                AppResult.Success(SelectInstrumentResult(card = info(sber, "old-A"), pointValue = 1.0))

            val vm = createVm()
            // Сначала установим selectedAccountId (как если бы карточка уже в слоте).
            vm.onAccountSaved("old-A")
            vm.onInstrumentSelected(sber)
            advanceUntilIdle()

            assertEquals("old-A", vm.state.value.selectedAccountId)
        }

    @Test
    fun `onInstrumentSelected failure keeps broker, accountId null`() =
        runTest {
            coEvery { selectInstrumentUseCase.execute(sber, null) } returns
                AppResult.Failure(AppError.Unknown("boom", null))

            val vm = createVm()
            vm.onInstrumentSelected(sber)
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(BrokerName.TINVEST, s.selectedBroker)
            assertNull(s.selectedAccountId)
        }

    @Test
    fun `onPairedInstrumentSelected sets pairedBroker and null pairedAccountId`() =
        runTest {
            coEvery { selectPairedInstrumentUseCase.execute(tmon) } returns
                AppResult.Success(SelectPairedResult(price = 10.0, pointValue = 1.0))

            val vm = createVm()
            vm.onAccountSaved("A")
            vm.onPairedAccountSaved("X")
            vm.onPairedInstrumentSelected(tmon)
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(BrokerName.TINVEST, s.pairedBroker)
            assertNull(s.pairedAccountId)
            // primary slot unaffected.
            assertEquals("A", s.selectedAccountId)
        }

    @Test
    fun `onAccountSaved updates selectedAccountId`() =
        runTest {
            val vm = createVm()
            advanceUntilIdle()
            vm.onAccountSaved("A")
            assertEquals("A", vm.state.value.selectedAccountId)
            vm.onAccountSaved(null)
            assertNull(vm.state.value.selectedAccountId)
        }

    @Test
    fun `onPairedAccountSaved updates pairedAccountId`() =
        runTest {
            val vm = createVm()
            advanceUntilIdle()
            vm.onPairedAccountSaved("B")
            assertEquals("B", vm.state.value.pairedAccountId)
            vm.onPairedAccountSaved(null)
            assertNull(vm.state.value.pairedAccountId)
        }

    @Test
    fun `clearSelectedInstrument resets broker and accountId`() =
        runTest {
            val vm = createVm()
            advanceUntilIdle()
            vm.onAccountSaved("A")
            vm.clearSelectedInstrument()

            val s = vm.state.value
            assertEquals(BrokerName.TINVEST, s.selectedBroker)
            assertNull(s.selectedAccountId)
            assertNull(s.selectedInstrument)
        }

    @Test
    fun `clearPairedInstrument resets pairedBroker and pairedAccountId`() =
        runTest {
            val vm = createVm()
            advanceUntilIdle()
            vm.onPairedAccountSaved("B")
            vm.clearPairedInstrument()

            val s = vm.state.value
            assertEquals(BrokerName.TINVEST, s.pairedBroker)
            assertNull(s.pairedAccountId)
            assertNull(s.pairedInstrument)
        }

    @Test
    fun `restoreOrderForm sets broker from instrument and null accountId`() =
        runTest {
            val vm = createVm()
            // createVm() -> stubDefaults() ставит Failure; переопределяем
            // на Success ДО advanceUntilIdle (init-launch ещё не выполнен).
            coEvery { restoreOrderFormUseCase.execute() } returns
                AppResult.Success(
                    RestoredFormState(
                        selectedInstrument = sber,
                        pairedInstrument = tmon,
                        pairTradingEnabled = true,
                        quantity = 1L,
                        pairedMultiplier = 2L,
                    ),
                )
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(sber, s.selectedInstrument)
            assertEquals(BrokerName.TINVEST, s.selectedBroker)
            assertNull(s.selectedAccountId)
            assertEquals(tmon, s.pairedInstrument)
            assertEquals(BrokerName.TINVEST, s.pairedBroker)
            assertNull(s.pairedAccountId)
        }

    @Test
    fun `restoreOrderForm sets broker and account from card`() =
        runTest {
            val vm = createVm()
            val card =
                CardIdentity(
                    tscalpInstrumentId = "uid-sber",
                    brokerName = BrokerName.TINVEST,
                    accountId = "A",
                )
            coEvery { restoreOrderFormUseCase.execute() } returns
                AppResult.Success(
                    RestoredFormState(
                        selectedCard = card,
                        selectedInstrument = sber,
                        pairTradingEnabled = false,
                    ),
                )
            advanceUntilIdle()

            val s = vm.state.value
            assertEquals(sber, s.selectedInstrument)
            assertEquals(BrokerName.TINVEST, s.selectedBroker)
            assertEquals("A", s.selectedAccountId)
        }

    @Test
    fun `postOrder success emits StatusEvent`() =
        runTest {
            coEvery { selectInstrumentUseCase.execute(sber, null) } returns
                AppResult.Success(SelectInstrumentResult(card = info(sber, "A"), pointValue = 1.0))
            coEvery {
                calculateTradeDetails.calculate(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns TradeDetails(executionPrice = 100.0)
            coEvery { placeOrderUseCase.execute(any(), OrderDirection.BUY) } returns
                PlaceOrderOutcome.Success(
                    primary =
                        PrimaryOutcome.Regular(
                            orderId = "order-1",
                            executedLots = 3,
                            totalLots = 3,
                        ),
                    paired = null,
                    freeBalance = 100_000.0,
                )

            val vm = createVm()
            vm.onInstrumentSelected(sber)
            vm.onQuantityChanged("3")
            advanceUntilIdle()

            vm.onBuyClick()
            advanceUntilIdle()
            vm.onConfirmOrder()
            advanceUntilIdle()

            val event = vm.status.first()
            assertEquals(false, event.isError)
            assertTrue(event.message.contains("Заявка выполнена"))
            assertTrue(event.message.contains("order-1"))
        }
}
