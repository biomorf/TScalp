package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
import kotlinx.coroutines.CancellationException

/**
 * Выбор парного инструмента — разбиение `OrdersViewModel` на 4 VM, шаг 13c.
 *
 * Вынесено из `OrdersViewModel.onPairedInstrumentSelected` (clean-audit
 * M013, M2): `getLastPrices` + разбор `AppResult` — domain-orchestration,
 * не presentation.
 *
 * **Не в scope** (VM-часть, 1:1, остаётся в Coord до 13d):
 * `_uiState.update` (`pairedInstrument` / `pairCurrentPrice` /
 * `pairedPointValue`), `syncPriceInterest`, `updateTradeDetails`,
 * `updatePairExecDetails`, `saveState`.
 *
 * **Persistence — не в usecase.** `onPairedInstrumentSelected` в конце
 * вызывает `saveState()`, который 1:1 сохраняет `pairedTscalpInstrumentId`
 * (см. `OrdersViewModel.saveState`, K8a). Сохранение в usecase дало бы
 * дубль (2 DataStore write). Usecase возвращает `SelectPairedResult`,
 * VM-часть сохраняет paired uid через `saveState()`.
 *
 * **`getLastPrices` failure** — не ошибка: `price = null`,
 * `pointValue` вычисляется (1:1: VM обновляет `pairCurrentPrice` /
 * `pairedPointValue` только при `price != null`).
 *
 * @return `AppResult.Success(SelectPairedResult)`;
 *   `AppResult.Failure(AppError.Unknown)` на исключении
 *   `getLastPricesResult`. `CancellationException` пробрасывается.
 */
class SelectPairedInstrumentUseCase
    constructor(
        private val marketDataRepository: MarketDataRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "SelectPairedInstrumentUseCase"
        }

        suspend fun execute(instrument: Instrument): AppResult<SelectPairedResult> {
            logger.d(TAG, "execute: ticker=${instrument.ticker}")
            return try {
                val price = fetchPrice(instrument.tscalpInstrumentId)
                AppResult.Success(
                    SelectPairedResult(
                        price = price,
                        pointValue = instrument.moneyPerPointOfPrice,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "select paired exception", e)
                AppResult.Failure(
                    AppError.Unknown("Ошибка выбора парного инструмента: ${e.message}", e),
                )
            }
        }

        private suspend fun fetchPrice(tscalpInstrumentId: String): Double? =
            when (
                val pricesResult =
                    marketDataRepository.getLastPricesResult(listOf(tscalpInstrumentId))
            ) {
                is AppResult.Success -> pricesResult.data[tscalpInstrumentId]
                is AppResult.Failure -> {
                    logger.w(
                        TAG,
                        "getLastPrices failed: ${pricesResult.error.message}",
                        pricesResult.error.cause,
                    )
                    null
                }
            }
    }

/**
 * Результат [SelectPairedInstrumentUseCase]: цена и pointValue
 * парного инструмента.
 *
 * `price` — `null` при failure `getLastPrices` (не ошибка, 1:1).
 * `pointValue` — `instrument.moneyPerPointOfPrice`.
 */
data class SelectPairedResult(
    val price: Double?,
    val pointValue: Double,
)
