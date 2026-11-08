package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.findCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат [OpenCardDialogUseCase]: broker + accounts +
 * selectedAccountId — всё, что нужно `OrderDialogsState` для
 * открытия диалога карточки. Инструмент VM уже знает (передаёт
 * `Instrument` в [OpenCardDialogUseCase.execute]); в результате
 * он не дублируется.
 */
data class OpenCardDialogResult(
    val broker: BrokerName,
    val accounts: List<BrokerAccount>,
    val selectedAccountId: String?,
)

/**
 * Результат [OpenCardDialogUseCase.execute].
 *
 * **Почему не `AppResult`:** `Failure` должен нести [broker] — иначе
 * VM не может выставить `selectedBroker` 1:1 (см. `openBrokerDialog`
 * в `OrderDialogsViewModel`: `selectedBroker = existingCard?.brokerName
 * ?: TINVEST` даже на failure). Если `broker` неизвестен (поток
 * `identities` / `selected` бросил до резолва) — fallback `TINVEST`
 * (совпадает с «нет карточки»).
 */
sealed interface OpenCardDialogOutcome {
    data class Success(
        val result: OpenCardDialogResult,
    ) : OpenCardDialogOutcome

    data class Failure(
        val broker: BrokerName,
        val error: AppError,
    ) : OpenCardDialogOutcome
}

/**
 * Открытие диалога настроек карточки (брокер + счёт) — clean-audit
 * **M015** (open, M2 partial). Вынесено из
 * `OrderDialogsViewModel.openBrokerDialog` (findCard + selected
 * fallback + `LoadCardAccountsUseCase.execute`).
 *
 * **Не путать с [LoadCardAccountsUseCase]:** тот делает только
 * getAccounts + savedFallback; здесь — findCard по composite
 * `(tscalpInstrumentId, brokerName)` из `CardsRepository.identities` +
 * selected fallback из `AccountSelectionRepository.selected(broker)` +
 * `sandboxMode` из `SandboxPreferencesRepository` + делегирование в
 * `LoadCardAccountsUseCase`.
 *
 * **Не путать с `loadDialogAccounts` (VM):** тот — thin delegate
 * на `LoadCardAccountsUseCase` (M2-fixed in 12a, не M2).
 *
 * **Failure из [LoadCardAccountsUseCase]** — `OpenCardDialogOutcome.Failure(broker, error)`,
 * не `AppResult.Failure`: [broker] нужен VM для 1:1 `selectedBroker`
 * (см. `OrderDialogsViewModel.openBrokerDialog`). VM разбирает failure
 * 1:1: `showBrokerDialog = true`, `dialogInstrument = instrument`,
 * `selectedBroker = outcome.broker`, `dialogAccounts = emptyList()`,
 * `selectedAccountIdDialog = null`.
 *
 * Non-throwing (кроме `CancellationException` — rethrow).
 */
@Singleton
class OpenCardDialogUseCase
    @Inject
    constructor(
        private val cardsRepository: CardsRepository,
        private val accountSelectionRepository: AccountSelectionRepository,
        private val loadCardAccountsUseCase: LoadCardAccountsUseCase,
        private val sandboxPreferencesRepository: SandboxPreferencesRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "OpenCardDialogUseCase"
        }

        suspend fun execute(instrument: Instrument): OpenCardDialogOutcome {
            logger.d(
                TAG,
                "execute: uid=${instrument.tscalpInstrumentId}, broker=${instrument.brokerName}",
            )
            return try {
                val existingCard = cardsRepository.identities.first().findCard(instrument)
                val broker = existingCard?.brokerName ?: instrument.brokerName
                val savedAccountId =
                    existingCard?.accountId
                        ?: accountSelectionRepository.selected(broker).first()

                when (
                    val result =
                        loadCardAccountsUseCase.execute(
                            brokerName = broker,
                            sandboxMode = sandboxPreferencesRepository.isSandboxMode(),
                            savedAccountId = savedAccountId,
                        )
                ) {
                    is AppResult.Success ->
                        OpenCardDialogOutcome.Success(
                            OpenCardDialogResult(
                                broker = broker,
                                accounts = result.data.accounts,
                                selectedAccountId = result.data.selectedAccountId,
                            ),
                        )
                    is AppResult.Failure -> {
                        logger.e(
                            TAG,
                            "loadCardAccounts failed: uid=${instrument.tscalpInstrumentId}, " +
                                "broker=${broker.displayName}: ${result.error.message}",
                            result.error.cause,
                        )
                        OpenCardDialogOutcome.Failure(broker = broker, error = result.error)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "open card dialog exception: uid=${instrument.tscalpInstrumentId}", e)
                OpenCardDialogOutcome.Failure(
                    broker = BrokerName.TINVEST,
                    error = AppError.Unknown("Ошибка открытия диалога карточки: ${e.message}", e),
                )
            }
        }
    }
