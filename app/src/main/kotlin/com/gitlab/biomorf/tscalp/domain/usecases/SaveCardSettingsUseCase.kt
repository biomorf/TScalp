package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.withEnsuredCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохранение настроек карточки (счёт) — разбиение `OrdersViewModel`
 * на 4 VM, шаг 7; пересмотрено в Фазе 1b-11 (модель A).
 *
 * **Ensure-add** (1b-11): `CardIdentity` — запись истории «Последних
 * просмотренных». Один `(uid, broker)` может иметь много записей
 * (разные счета = разные карточки). Смена счёта в слоте = «ensure
 * target exists»: старая запись **остаётся** в истории.
 *
 * **Нет коллизии**: если `(uid, broker, newAccountId)` уже есть —
 * это нормально (слот указывает на существующую запись). Никакого
 * `Failure("account taken")`, никакого disabled в UI.
 *
 * **Идемпотентно:** повторный save с тем же `newAccountId` — no-op
 * (`findCard` находит, `withEnsuredCard` не добавляет).
 *
 * **`newAccountId = null`** — валидный кейс (карточка без счёта).
 * ensure с `null` тоже идемпотентен.
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
            newAccountId: String?,
        ): AppResult<Unit> {
            logger.d(
                TAG,
                "execute: uid=${instrument.tscalpInstrumentId}, broker=${instrument.brokerName}, " +
                    "newAccountId=$newAccountId",
            )
            return try {
                val currentCards = cardsRepository.identities.first()
                val updated =
                    currentCards.withEnsuredCard(
                        tscalpInstrumentId = instrument.tscalpInstrumentId,
                        brokerName = instrument.brokerName,
                        newAccountId = newAccountId,
                    )
                if (updated == currentCards) {
                    logger.d(TAG, "already exists — no-op")
                } else {
                    cardsRepository.save(updated)
                }
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "save card settings exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка сохранения настроек карточки: ${e.message}", e))
            }
        }
    }
