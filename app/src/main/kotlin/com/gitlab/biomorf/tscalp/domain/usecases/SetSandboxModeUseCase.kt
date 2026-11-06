package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Переключение режима песочницы T-Invest + перезагрузка счетов
 * (clean-audit M006, M2).
 *
 * Вынесено из `TInvestSettingsViewModel.setSandboxMode` (при ревью
 * A2 найдено — не 1 делегирование, а 2 шага orchestration):
 * 1. сохранить режим в DataStore;
 * 2. перезагрузить счета в новом режиме (через
 *    [LoadBrokerAccountsUseCase] — domain -> domain, как
 *    [InitializeBrokerUseCase] оркестрирует manager'ы).
 *
 * **Не путать с [OpenSandboxAccountUseCase]:** тот создаёт
 * новый счёт; этот — только меняет режим (видимость sandbox /
 * prod счетов).
 */
@Singleton
class SetSandboxModeUseCase
    @Inject
    constructor(
        private val sandboxPreferencesRepository: SandboxPreferencesRepository,
        private val loadBrokerAccountsUseCase: LoadBrokerAccountsUseCase,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "SetSandboxModeUseCase"
        }

        /**
         * Сохраняет режим [enabled] и загружает счета T-Invest
         * в новом режиме.
         *
         * @return `AppResult.Success` с [LoadAccountsResult];
         *   `AppResult.Failure` — ошибка сохранения режима или
         *   загрузки счетов.
         */
        suspend fun execute(enabled: Boolean): AppResult<LoadAccountsResult> {
            logger.d(TAG, "execute: enabled=$enabled")
            return try {
                sandboxPreferencesRepository.setSandboxMode(enabled)
                loadBrokerAccountsUseCase.execute(BrokerName.TINVEST, sandboxMode = enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "setSandboxMode exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка смены режима: ${e.message}", e))
            }
        }
    }
