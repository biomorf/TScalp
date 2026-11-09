package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStatusRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccountType
import com.gitlab.biomorf.tscalp.domain.models.BrokerInitState
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.usecases.CheckBrokerInitializationUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.LoadOrdersAccountsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.OrdersAccountsResult
import com.gitlab.biomorf.tscalp.domain.usecases.RefreshSelectedInstrumentsUseCase
import com.gitlab.biomorf.tscalp.domain.usecases.UpdateTradingStatusesUseCase
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val portfolioRepository = mockk<PortfolioRepository>(relaxed = true)
    private val instrumentRepo = mockk<InstrumentRepository>(relaxed = true)
    private val sandboxPreferencesRepository = mockk<SandboxPreferencesRepository>(relaxed = true)
    private val accountSelectionRepository = mockk<AccountSelectionRepository>(relaxed = true)
    private val cardsRepository = mockk<CardsRepository>(relaxed = true)
    private val brokerManager = mockk<BrokerRegistry>(relaxed = true)
    private val loadOrdersAccountsUseCase = mockk<LoadOrdersAccountsUseCase>(relaxed = true)
    private val refreshSelectedInstrumentsUseCase = mockk<RefreshSelectedInstrumentsUseCase>(relaxed = true)
    private val positionFlowManager = mockk<PositionFlowManager>(relaxed = true)
    private val checkBrokerInitializationUseCase = mockk<CheckBrokerInitializationUseCase>(relaxed = true)
    private val updateTradingStatusesUseCase = mockk<UpdateTradingStatusesUseCase>(relaxed = true)
    private val tradingStatusRepository = mockk<TradingStatusRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val acc1 = BrokerAccount(id = "acc-1", name = "A1", type = BrokerAccountType.BROKER)

    private fun createVm(): OrdersViewModel {
        every { brokerManager.anyInitialized } returns MutableStateFlow(false)
        every { tradingStatusRepository.statuses } returns MutableStateFlow(emptyMap())
        every { cardsRepository.identities } returns flowOf(emptyList())
        coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
        coEvery { checkBrokerInitializationUseCase.execute(any()) } returns
            AppResult.Success(BrokerInitState.NoneInitialized)
        return OrdersViewModel(
            portfolioRepository,
            instrumentRepo,
            sandboxPreferencesRepository,
            accountSelectionRepository,
            cardsRepository,
            brokerManager,
            loadOrdersAccountsUseCase,
            refreshSelectedInstrumentsUseCase,
            positionFlowManager,
            checkBrokerInitializationUseCase,
            updateTradingStatusesUseCase,
            tradingStatusRepository,
            logger,
        )
    }

    @Test
    fun `loadAccounts success emits StatusEvent`() =
        runTest {
            coEvery {
                loadOrdersAccountsUseCase.execute(BrokerName.TINVEST, false)
            } returns
                AppResult.Success(
                    OrdersAccountsResult(
                        accounts = listOf(acc1),
                        selectedAccountId = "acc-1",
                    ),
                )

            val vm = createVm()
            try {
                vm.loadAccounts()
                runCurrent()

                val event =
                    vm.coordStatus
                        .first()
                assertEquals(false, event.isError)
                assertTrue(event.message.contains("Загружено"))
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `loadAccounts failure emits StatusEvent`() =
        runTest {
            coEvery {
                loadOrdersAccountsUseCase.execute(BrokerName.TINVEST, false)
            } returns AppResult.Failure(AppError.Network("down", null))

            val vm = createVm()
            try {
                vm.loadAccounts()
                runCurrent()

                val event =
                    vm.coordStatus
                        .first()
                assertEquals(true, event.isError)
                assertTrue(event.message.contains("Ошибка загрузки счетов"))
            } finally {
                vm.viewModelScope.cancel()
            }
        }
}
