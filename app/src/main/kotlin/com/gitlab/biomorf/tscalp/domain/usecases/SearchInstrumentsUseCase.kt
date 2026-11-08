package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.SearchCacheRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Поиск инструментов + обновление trading availability — разбиение
 * `OrdersViewModel` на 4 VM, шаг 11a (P6 ROADMAP).
 *
 * Вынесено из `OrdersViewModel.onSearchQueryChanged` / `onPairSearchQueryChanged`
 * (clean-audit M013 / M2): search + statuses + side-effect в
 * `TradingStatusRepository` через [UpdateTradingStatusesUseCase] —
 * domain-orchestration, не presentation.
 *
 * **Statuses — secondary.** Failure от [UpdateTradingStatusesUseCase]
 * не валит поиск: search уже успешен, UI получит инструменты; статусы
 * подтянутся следующим тиком. `logger.w` фиксирует.
 *
 * **`BrokerName.TINVEST` для статусов** (1:1 старое `updateTradingStatuses`):
 * `getTradingStatusesResult` ходит в T-Invest; для BCS/Finam uids статусы
 * всё равно пусты, поэтому запрос пропускается (skip для не-TINVEST).
 * Функционально эквивалентно старому поведению, меньше лишних вызовов.
 */
@Singleton
class SearchInstrumentsUseCase
    @Inject
    constructor(
        private val searchCacheRepository: SearchCacheRepository,
        private val updateTradingStatusesUseCase: UpdateTradingStatusesUseCase,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "SearchInstrumentsUseCase"
        }

        suspend fun execute(
            brokerName: BrokerName,
            query: String,
        ): AppResult<List<Instrument>> {
            logger.d(TAG, "execute: broker=$brokerName, query=$query")
            return try {
                when (val searchResult = searchCacheRepository.search(brokerName, query)) {
                    is AppResult.Success -> {
                        val instruments = searchResult.data
                        if (instruments.isNotEmpty() && brokerName == BrokerName.TINVEST) {
                            updateStatuses(instruments.map { it.tscalpInstrumentId })
                        }
                        searchResult
                    }
                    is AppResult.Failure -> searchResult
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "search instruments exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка поиска: ${e.message}", e))
            }
        }

        private suspend fun updateStatuses(ids: List<String>) {
            val result = updateTradingStatusesUseCase.execute(BrokerName.TINVEST, ids)
            if (result is AppResult.Failure) {
                logger.w(
                    TAG,
                    "statuses update failed (search OK): ${result.error.message}",
                    result.error.cause,
                )
            }
        }
    }
