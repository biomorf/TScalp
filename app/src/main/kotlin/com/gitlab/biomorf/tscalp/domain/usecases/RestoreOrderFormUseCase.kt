package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
import kotlinx.coroutines.CancellationException

/**
 * Результат восстановления формы заявки — снимок для `OrderFormState`.
 *
 * 6 полей из [com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot]
 * + 2 computed (`currentPointValue` / `pairedPointValue` — из
 * [moneyPerPointOfPrice] найденного [Instrument]).
 */
data class RestoredFormState(
    val selectedInstrument: Instrument? = null,
    val pairedInstrument: Instrument? = null,
    val pairTradingEnabled: Boolean = false,
    val quantity: Long = 1L,
    val pairedMultiplier: Long = 10L,
    val orderType: OrderTypeSelection = OrderTypeSelection.Market,
    val currentPointValue: Double? = null,
    val pairedPointValue: Double? = null,
)

/**
 * Восстановление формы заявки из сохранённого торгового состояния
 * сессии — разбиение `OrdersViewModel` на 4 VM, шаг 13a.
 *
 * Вынесено из `OrdersViewModel.restoreState()` (clean-audit M013, M2):
 * `tradingStateRepository.load()` + `getInstrument(uid)` × 2 +
 * `brokerReady` + pointValue — domain-orchestration, не presentation.
 *
 * **Не в scope** (VM-часть, 1:1, остаётся в Coord до 13d):
 * `_uiState.update` (selected/paired/currentPointValue/pairedPointValue/
 * pairTradingEnabled/quantity/pairedMultiplier/orderType),
 * `syncPriceInterest`, `updateTradeDetails`, `updatePairExecDetails`.
 *
 * **`brokerReady`** — через [BrokerRegistry.getDefaultBroker] (может
 * бросить `IllegalStateException`, если default не зарегистрирован;
 * ловится -> `false`). `false` -> `getInstrument` не вызывается,
 * instruments = `null`; snapshot-поля (pairTradingEnabled / quantity /
 * pairedMultiplier / orderType) сохраняются (1:1 с оригиналом).
 *
 * **`getInstrument = null`** (instrument не найден) -> поле `null`,
 * не `Failure` (1:1 с оригиналом: `_uiState` просто не обновляется
 * для этого инструмента).
 *
 * @return `AppResult.Success(RestoredFormState)`;
 *   `AppResult.Failure(AppError.Unknown)` на исключении
 *   DataStore / repository. `CancellationException` пробрасывается.
 */
class RestoreOrderFormUseCase
    constructor(
        private val tradingStateRepository: TradingStateRepository,
        private val instrumentRepository: InstrumentRepository,
        private val brokerRegistry: BrokerRegistry,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "RestoreOrderFormUseCase"
        }

        suspend fun execute(): AppResult<RestoredFormState> {
            logger.d(TAG, "execute")
            return try {
                val brokerReady =
                    try {
                        brokerRegistry.getDefaultBroker().isInitialized
                    } catch (e: Exception) {
                        logger.w(TAG, "getDefaultBroker failed: ${e.message}", e)
                        false
                    }

                val snapshot = tradingStateRepository.load()

                val selectedInstrument =
                    if (snapshot.selectedTscalpInstrumentId != null && brokerReady) {
                        instrumentRepository.getInstrument(snapshot.selectedTscalpInstrumentId)
                    } else {
                        null
                    }
                val pairedInstrument =
                    if (snapshot.pairedTscalpInstrumentId != null && brokerReady) {
                        instrumentRepository.getInstrument(snapshot.pairedTscalpInstrumentId)
                    } else {
                        null
                    }

                AppResult.Success(
                    RestoredFormState(
                        selectedInstrument = selectedInstrument,
                        pairedInstrument = pairedInstrument,
                        pairTradingEnabled = snapshot.pairTradingEnabled,
                        quantity = snapshot.quantity,
                        pairedMultiplier = snapshot.pairedMultiplier,
                        orderType = snapshot.orderType,
                        currentPointValue = selectedInstrument?.moneyPerPointOfPrice,
                        pairedPointValue = pairedInstrument?.moneyPerPointOfPrice,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "restore failed: ${e.message}", e)
                AppResult.Failure(
                    AppError.Unknown("Ошибка восстановления формы: ${e.message}", e),
                )
            }
        }
    }
