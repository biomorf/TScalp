package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.api.managers.OrderFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.usecases.CancelOrderUseCase
import com.gitlab.biomorf.tscalp.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val accountsRepository = mockk<AccountsRepository>(relaxed = true)
    private val cancelOrderUseCase = mockk<CancelOrderUseCase>(relaxed = true)
    private val sandboxPreferencesRepository = mockk<SandboxPreferencesRepository>(relaxed = true)
    private val orderFlowManager = mockk<OrderFlowManager>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private fun createVm(): OrdersListViewModel {
        every { orderFlowManager.orders } returns MutableStateFlow(emptyList())
        every { orderFlowManager.hasLoaded } returns MutableStateFlow(false)
        coEvery { sandboxPreferencesRepository.isSandboxMode() } returns false
        return OrdersListViewModel(
            accountsRepository,
            cancelOrderUseCase,
            sandboxPreferencesRepository,
            orderFlowManager,
            logger,
        )
    }

    @Test
    fun `loadOrders getAccounts failure emits StatusEvent`() =
        runTest {
            coEvery {
                accountsRepository.getAccountsResult(BrokerName.TINVEST, false)
            } returns AppResult.Failure(AppError.Network("down", null))

            val vm = createVm()
            vm.loadOrders()
            advanceUntilIdle()

            val event = vm.status.first()
            assertEquals(true, event.isError)
            assertTrue(event.message.contains("Ошибка"))
            assertTrue(event.message.contains("down"))
        }
}
