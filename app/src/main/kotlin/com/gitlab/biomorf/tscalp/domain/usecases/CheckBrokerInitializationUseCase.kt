package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceConsumer
import com.gitlab.biomorf.tscalp.domain.api.managers.PriceFlowManager
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerInitState
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проверка инициализации брокеров + cleanup при `NotInitialized`.
 *
 * Вынесено из `OrdersViewModel.checkApiInitialization` /
 * `PortfolioViewModel.checkApiInitialization` (clean-audit M013, M2):
 * проверка «хотя бы один брокер инициализирован» и симметричный
 * cleanup (`clearInterest` + `positionFlowManager.stop()`) — domain-
 * логика, не presentation.
 *
 * VM-часть (по Q2 + F3) остаётся в VM: при `Initialized` —
 * `loadAccounts()` / `loadPortfolio()` / `syncPriceInterest()`; при
 * `NotInitialized` — `stopPositionUpdates()` и очистка `UiState`.
 */
@Singleton
class CheckBrokerInitializationUseCase
    @Inject
    constructor(
        private val brokerRegistry: BrokerRegistry,
        private val priceFlowManager: PriceFlowManager,
        private val positionFlowManager: PositionFlowManager,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "CheckBrokerInitializationUseCase"
        }

        /**
         * Проверяет инициализацию; при `NotInitialized` — снимает
         * интерес к ценам и останавливает `PositionFlowManager`.
         *
         * @param consumer — consumer, чей интерес снимается при cleanup
         *   (`PriceConsumer.ORDERS` или `PriceConsumer.PORTFOLIO`).
         * @return `AppResult.Success(AnyInitialized / NoneInitialized)` или
         *   `AppResult.Failure` (ошибка проверки/cleanup).
         */
        suspend fun execute(consumer: PriceConsumer): AppResult<BrokerInitState> {
            logger.d(TAG, "execute: consumer=$consumer")
            return try {
                val isAnyInit = brokerRegistry.getAllBrokers().any { it.isInitialized }
                if (isAnyInit) {
                    AppResult.Success(BrokerInitState.AnyInitialized)
                } else {
                    priceFlowManager.clearInterest(consumer)
                    positionFlowManager.stop()
                    AppResult.Success(BrokerInitState.NoneInitialized)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "check initialization exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка проверки инициализации: ${e.message}", e))
            }
        }
    }
