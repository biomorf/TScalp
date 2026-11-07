package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStatusRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Обновление trading availability по списку `tscalpInstrumentId`
 * — разбиение `OrdersViewModel` на 4 VM, шаг 8.
 *
 * Вынесено из `OrdersViewModel.updateTradingStatuses` (clean-audit
 * M013, M2): `getTradingStatusesResult` + сохранение в
 * `TradingStatusRepository` — domain-orchestration.
 *
 * **Merge** (`statuses + incoming`) делает
 * [TradingStatusRepositoryImpl.update][com.gitlab.biomorf.tscalp.data.repository.TradingStatusRepositoryImpl],
 * не usecase (1:1 с оригиналом).
 *
 * **Empty ids** — `AppResult.Success(Unit)` без вызова repo
 * (1:1 `if (ids.isEmpty()) return`).
 */
@Singleton
class UpdateTradingStatusesUseCase
    @Inject
    constructor(
        private val marketDataRepository: MarketDataRepository,
        private val tradingStatusRepository: TradingStatusRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "UpdateTradingStatusesUseCase"
        }

        suspend fun execute(
            brokerName: BrokerName,
            ids: List<String>,
        ): AppResult<Unit> {
            if (ids.isEmpty()) return AppResult.Success(Unit)
            logger.d(TAG, "execute: brokerName=$brokerName, ids=${ids.size}")
            return try {
                when (val result = marketDataRepository.getTradingStatusesResult(brokerName, ids)) {
                    is AppResult.Success -> {
                        tradingStatusRepository.update(result.data)
                        AppResult.Success(Unit)
                    }
                    is AppResult.Failure -> {
                        logger.e(TAG, "Ошибка обновления статусов: ${result.error.message}", result.error.cause)
                        result
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "update trading statuses exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка обновления статусов: ${e.message}", e))
            }
        }
    }
