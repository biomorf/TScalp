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
 * **История (Coord удалён в Phase 6.1):** `syncPriceInterest()` жил в
 * `OrderFormViewModel` до C2; теперь вызывается из 4 sub-VM
 * (`OrderInstrumentViewModel.onInstrumentSelected` и др.).
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
