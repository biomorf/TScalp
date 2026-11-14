package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.managers.PriceConsumer
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager

/**
 * Sync price interest: обёртка над `PriceFlowManager.setInterest`.
 *
 * **2c (fixation, C2 PR-2):** usecase принимает готовый
 * `tscalpIds: Set<String>` — presentation собирает его из
 * `OrderInstrumentState` (sub-state). Domain не знает про sub-state.
 *
 * **2d (временное):** `OrderFormViewModel.syncPriceInterest()` (private)
 * остаётся до PR-3, где 4 sub-VM переезжают на этот usecase.
 */
class SyncPriceInterestUseCase(
    private val priceFlowManager: PriceFlowManager,
) {
    fun execute(
        tscalpIds: Set<String>,
        consumer: PriceConsumer = PriceConsumer.ORDERS,
    ) {
        priceFlowManager.setInterest(consumer, tscalpIds)
    }
}
