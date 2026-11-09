package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.managers.OrderFlowManager
import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отключение брокера: очистка credentials + деинициализация +
 * остановка менеджеров (TInvest) + refresh флага инициализации.
 *
 * Вынесено из `SettingsViewModel.clearBrokerCredentials`
 * (clean-audit M006, M2): ~50 строк orchestration — domain-логика,
 * не presentation. Симметрично [InitializeBrokerUseCase].
 *
 * Порядок шагов 1:1 с прежним `clearBrokerCredentials`:
 * 1. очистка credentials (repo);
 * 2. для TInvest — `tradingStateRepository.clear()`;
 * 3. деинициализация lifecycle ([BrokerApi.lifecycleTInvest] /
 *    `lifecycleBcs` / `lifecycleFinam`; `null` — no-op);
 * 4. для TInvest — `positionFlowManager.stop()` и
 *    `orderFlowManager.stop()` (сразу, не дожидаясь создания
 *    PortfolioViewModel);
 * 5. `brokerRegistry.refreshInitializationState()` — пересчёт
 *    `anyInitialized` (иначе `state.api` остаётся non-null,
 *    `isInitialized` возвращает `true`, флаг не переключается).
 *
 * `_uiState.update` (`*Connected = false`, `defaultAccountId = ""`)
 * — presentation, остаётся в panel-VM.
 */
@Singleton
class DisconnectBrokerUseCase
    @Inject
    constructor(
        private val brokerCredentialsRepository: BrokerCredentialsRepository,
        private val tradingStateRepository: TradingStateRepository,
        private val brokerRegistry: BrokerRegistry,
        private val positionFlowManager: PositionFlowManager,
        private val orderFlowManager: OrderFlowManager,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "DisconnectBrokerUseCase"
        }

        /**
         * Отключает брокера [brokerName].
         *
         * @return `AppResult.Success(Unit)` — успех;
         *   `AppResult.Failure` — ошибка (в `message` готовая строка
         *   для `StatusEvent.message`).
         */
        suspend fun execute(brokerName: BrokerName): AppResult<Unit> {
            logger.d(TAG, "execute: brokerName=$brokerName")
            return try {
                brokerCredentialsRepository.clearBrokerCredentials(brokerName)

                if (brokerName == BrokerName.TINVEST) {
                    tradingStateRepository.clear()
                }

                when (brokerName) {
                    BrokerName.TINVEST -> brokerRegistry.getBroker(BrokerName.TINVEST)?.lifecycleTInvest?.deinitialize()
                    BrokerName.BCS -> brokerRegistry.getBroker(BrokerName.BCS)?.lifecycleBcs?.deinitialize()
                    BrokerName.FINAM -> brokerRegistry.getBroker(BrokerName.FINAM)?.lifecycleFinam?.deinitialize()
                }

                if (brokerName == BrokerName.TINVEST) {
                    positionFlowManager.stop()
                    orderFlowManager.stop()
                }

                brokerRegistry.refreshInitializationState()

                logger.d(TAG, "disconnect: success for $brokerName")
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "disconnect failed for $brokerName", e)
                AppResult.Failure(AppError.Unknown("Ошибка отключения: ${e.message}", e))
            }
        }
    }
