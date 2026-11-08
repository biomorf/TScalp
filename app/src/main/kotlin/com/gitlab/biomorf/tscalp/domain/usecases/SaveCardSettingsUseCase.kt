package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.findCard
import com.gitlab.biomorf.tscalp.domain.models.withUpdatedSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохранение настроек карточки (брокер + счёт) —
 * разбиение `OrdersViewModel` на 4 VM, шаг 7.
 *
 * Вынесено из `OrdersViewModel.saveBrokerSettings` (clean-audit
 * M013, M2): обновление `CardsRepository` для composite identity
 * `(tscalpInstrumentId, brokerName)` — domain-orchestration.
 *
 * **UI-часть** (`_state.update` с `showBrokerDialog=false` /
 * `dialogInstrument=null` / `swipeResetTrigger` toggle)
 * остаётся в VM (Dialogs, 1:1).
 *
 * **No-op при отсутствии карточки** — `AppResult.Success(Unit)`,
 * `save` не вызывается (1:1 по эффекту с оригиналом; сценарий
 * «карточка удалена, диалог открыт» не должен ломаться).
 */
@Singleton
class SaveCardSettingsUseCase
    @Inject
    constructor(
        private val cardsRepository: CardsRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "SaveCardSettingsUseCase"
        }

        suspend fun execute(
            instrument: Instrument,
            accountId: String?,
        ): AppResult<Unit> {
            logger.d(
                TAG,
                "execute: uid=${instrument.tscalpInstrumentId}, broker=${instrument.brokerName}, accountId=$accountId",
            )
            return try {
                val currentCards = cardsRepository.identities.first()
                if (currentCards.findCard(instrument) == null) {
                    logger.w(
                        TAG,
                        "card not found: uid=${instrument.tscalpInstrumentId}, broker=${instrument.brokerName} — no-op",
                    )
                    return AppResult.Success(Unit)
                }
                val updated =
                    currentCards.withUpdatedSettings(
                        tscalpInstrumentId = instrument.tscalpInstrumentId,
                        brokerName = instrument.brokerName,
                        accountId = accountId,
                    )
                cardsRepository.save(updated)
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "save card settings exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка сохранения настроек карточки: ${e.message}", e))
            }
        }
    }
