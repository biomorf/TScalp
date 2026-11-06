package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.DebugFlagsRepository
import com.gitlab.biomorf.tscalp.domain.api.managers.OrderFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.log.Logger
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class ToggleDebugFlagUseCaseTest {
    private val debugFlagsRepository = mockk<DebugFlagsRepository>(relaxed = true)
    private val priceFlowManager = mockk<PriceFlowManager>(relaxed = true)
    private val positionFlowManager = mockk<PositionFlowManager>(relaxed = true)
    private val orderFlowManager = mockk<OrderFlowManager>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)

    private val useCase =
        ToggleDebugFlagUseCase(
            debugFlagsRepository,
            priceFlowManager,
            positionFlowManager,
            orderFlowManager,
            logger,
        )

    @Test
    fun `GRPC toggles disableGrpc and reapplies on 3 managers`() {
        useCase.execute(DebugFlag.GRPC, enabled = true)

        verify(exactly = 1) { debugFlagsRepository.disableGrpc = true }
        verify(exactly = 0) { debugFlagsRepository.disableWs = any() }
        verify(exactly = 0) { debugFlagsRepository.forceRest = any() }
        verify(exactly = 1) { priceFlowManager.reapplyDebugFlags() }
        verify(exactly = 1) { positionFlowManager.reapplyDebugFlags() }
        verify(exactly = 1) { orderFlowManager.reapplyDebugFlags() }
    }

    @Test
    fun `WS toggles disableWs and reapplies on 3 managers`() {
        useCase.execute(DebugFlag.WS, enabled = false)

        verify(exactly = 1) { debugFlagsRepository.disableWs = false }
        verify(exactly = 0) { debugFlagsRepository.disableGrpc = any() }
        verify(exactly = 0) { debugFlagsRepository.forceRest = any() }
        verify(exactly = 1) { priceFlowManager.reapplyDebugFlags() }
        verify(exactly = 1) { positionFlowManager.reapplyDebugFlags() }
        verify(exactly = 1) { orderFlowManager.reapplyDebugFlags() }
    }

    @Test
    fun `REST toggles forceRest and reapplies on 3 managers`() {
        useCase.execute(DebugFlag.REST, enabled = true)

        verify(exactly = 1) { debugFlagsRepository.forceRest = true }
        verify(exactly = 0) { debugFlagsRepository.disableGrpc = any() }
        verify(exactly = 0) { debugFlagsRepository.disableWs = any() }
        verify(exactly = 1) { priceFlowManager.reapplyDebugFlags() }
        verify(exactly = 1) { positionFlowManager.reapplyDebugFlags() }
        verify(exactly = 1) { orderFlowManager.reapplyDebugFlags() }
    }
}
