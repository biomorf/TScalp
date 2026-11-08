package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.findCard
import com.gitlab.biomorf.tscalp.domain.models.withRecreatedSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохранение настроек карточки (счёт) — разбиение `OrdersViewModel`
 * на 4 VM, шаг 7; пересмотрено в Фазе 1b.
 *
 * **Пересоздание identity** (Фаза 1b): `accountId` — часть ключа
 * `CardIdentity` (см. `docs/architecture.md` → «CardIdentity и слоты»).
 * Смена счёта = удалить `(uid, broker, oldAccountId)` + добавить
 * `(uid, broker, newAccountId)`. Семантика «мутировать accountId
 * существующей карточки» (Фаза 1a) — отменена.
 *
 * **Коллизия:** если `(uid, broker, newAccountId)` уже занята другой
 * карточкой — `AppResult.Failure("account taken")`. UI не даёт выбрать
 * занятый счёт (disabled в `BrokerAccountDialog`, Фаза 1b-4); это
 * вторая защита от race.
 *
 * **No-op:** `oldAccountId == newAccountId` — ничего не меняется
 * (пользователь нажал «Сохранить» без изменения). `Success(Unit)`,
 * `save` не вызывается.
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
            oldAccountId: String?,
            newAccountId: String?,
        ): AppResult<Unit> {
            logger.d(
                TAG,
                "execute: uid=${instrument.tscalpInstrumentId}, broker=${instrument.brokerName}, " +
                    "oldAccountId=$oldAccountId, newAccountId=$newAccountId",
            )
            return try {
                if (oldAccountId == newAccountId) {
                    logger.d(TAG, "same account — no-op")
                    return AppResult.Success(Unit)
                }
                val currentCards = cardsRepository.identities.first()
                val collision = currentCards.findCard(instrument, newAccountId)
                if (collision != null) {
                    logger.w(
                        TAG,
                        "account taken: uid=${instrument.tscalpInstrumentId}, " +
                            "broker=${instrument.brokerName}, accountId=$newAccountId",
                    )
                    return AppResult.Failure(AppError.Unknown("account taken: $newAccountId"))
                }
                val updated =
                    currentCards.withRecreatedSettings(
                        tscalpInstrumentId = instrument.tscalpInstrumentId,
                        brokerName = instrument.brokerName,
                        oldAccountId = oldAccountId,
                        newAccountId = newAccountId,
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
