package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot

/**
 * Persist формы заявки: обёртка над `TradingStateRepository.save`.
 *
 * **2c (fixation, C2 PR-2):** usecase принимает готовый domain-DTO
 * [TradingStateSnapshot] — presentation-маппинг (`OrderFormState` →
 * sub-state → snapshot) в VM. Domain не знает про presentation-state.
 *
 * **2d (временное):** `OrderFormViewModel.saveState()` (private) остаётся
 * до PR-3, где 4 sub-VM переезжают на этот usecase. Unblocks PR-3.
 */
class SaveFormStateUseCase(
    private val tradingStateRepository: TradingStateRepository,
) {
    suspend fun execute(snapshot: TradingStateSnapshot) {
        tradingStateRepository.save(snapshot)
    }
}
