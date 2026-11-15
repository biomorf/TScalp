package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import com.gitlab.biomorf.tscalp.domain.usecases.SaveFormStateUseCase
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderFormMediatorTest {
    private val saveFormStateUseCase = mockk<SaveFormStateUseCase>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val card1 = CardIdentity("uid-1", BrokerName.TINVEST, "acc-1")
    private val card2 = CardIdentity("uid-2", BrokerName.BCS, "acc-2")

    @Test
    fun `updatePrimitives emits to shared immediately`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val mediator = OrderFormMediator(saveFormStateUseCase, logger, scope)

            mediator.updatePrimitives(
                selectedCard = card1,
                pairedCard = null,
                pairTradingEnabled = false,
                quantity = 5L,
                pairedMultiplier = 10L,
                orderType = OrderTypeSelection.Market,
            )

            val s = mediator.shared.value
            assertEquals(card1, s.selectedCard)
            assertEquals(5L, s.quantity)
            assertEquals(OrderTypeSelection.Market, s.orderType)
        }

    @Test
    fun `updatePrimitives debounces save calls`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val mediator = OrderFormMediator(saveFormStateUseCase, logger, scope)

            mediator.updatePrimitives(card1, null, false, 1L, 10L, OrderTypeSelection.Market)
            advanceTimeBy(100)
            mediator.updatePrimitives(card1, null, false, 2L, 10L, OrderTypeSelection.Market)
            advanceTimeBy(100)
            mediator.updatePrimitives(card1, null, false, 3L, 10L, OrderTypeSelection.Market)
            advanceTimeBy(OrderFormMediator.DEBOUNCE_MS + 100)
            advanceUntilIdle()

            coVerify(exactly = 1) { saveFormStateUseCase.execute(any()) }
        }

    @Test
    fun `conflated channel keeps latest`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val mediator = OrderFormMediator(saveFormStateUseCase, logger, scope)

            mediator.updatePrimitives(card1, card2, true, 5L, 20L, OrderTypeSelection.Limit)
            advanceTimeBy(OrderFormMediator.DEBOUNCE_MS + 100)
            advanceUntilIdle()

            coVerify {
                saveFormStateUseCase.execute(
                    TradingStateSnapshot(
                        selectedCard = card1,
                        pairedCard = card2,
                        pairTradingEnabled = true,
                        quantity = 5L,
                        pairedMultiplier = 20L,
                        orderType = OrderTypeSelection.Limit,
                    ),
                )
            }
        }
}
