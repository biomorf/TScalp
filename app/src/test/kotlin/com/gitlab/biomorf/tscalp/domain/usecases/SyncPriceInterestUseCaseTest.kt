package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.managers.PriceConsumer
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class SyncPriceInterestUseCaseTest {
    private val priceFlowManager = mockk<PriceFlowManager>(relaxed = true)
    private val useCase = SyncPriceInterestUseCase(priceFlowManager)

    @Test
    fun `execute with 2 ids passes set of 2 to setInterest`() {
        useCase.execute(setOf("a", "b"))
        verify { priceFlowManager.setInterest(PriceConsumer.ORDERS, setOf("a", "b")) }
    }

    @Test
    fun `execute with 1 id passes set of 1`() {
        useCase.execute(setOf("a"))
        verify { priceFlowManager.setInterest(PriceConsumer.ORDERS, setOf("a")) }
    }

    @Test
    fun `execute with empty set passes empty set`() {
        useCase.execute(emptySet())
        verify { priceFlowManager.setInterest(PriceConsumer.ORDERS, emptySet()) }
    }

    @Test
    fun `execute with explicit consumer uses it`() {
        useCase.execute(setOf("a"), PriceConsumer.PORTFOLIO)
        verify { priceFlowManager.setInterest(PriceConsumer.PORTFOLIO, setOf("a")) }
    }
}
