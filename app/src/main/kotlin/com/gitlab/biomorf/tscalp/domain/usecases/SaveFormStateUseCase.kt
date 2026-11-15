package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot

/**
 * Persist формы заявки: обёртка над `TradingStateRepository.save`.
 *
 * **2c (fixation, C2 PR-2):** usecase принимает готовый domain-DTO
 * [TradingStateSnapshot] — presentation-маппинг (`OrderFormState` →
 * sub-state → snapshot) в VM. Domain не знает про presentation-state.
 *
 * **История (Coord удалён в Phase 6.1):** `saveState()` жил в
 * `OrderFormViewModel` до C2; теперь персистит `OrderFormMediator`
 * через этот usecase (reactive, debounce 500ms).
 */
class SaveFormStateUseCase(
    private val tradingStateRepository: TradingStateRepository,
    private val logger: Logger,
) {
    suspend fun execute(snapshot: TradingStateSnapshot) {
        logger.d(
            TAG,
            "execute: primary=${snapshot.selectedCard?.tscalpInstrumentId} " +
                "pair=${snapshot.pairedCard?.tscalpInstrumentId} qty=${snapshot.quantity}",
        )
        tradingStateRepository.save(snapshot)
    }

    companion object {
        private const val TAG = "SaveFormStateUseCase"
    }
}
