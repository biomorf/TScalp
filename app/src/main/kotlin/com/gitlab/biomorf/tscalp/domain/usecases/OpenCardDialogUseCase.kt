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
 * selectedAccountId + oldAccountId — всё, что нужно
 * `OrderDialogsState` для открытия диалога карточки.
 *
 * `oldAccountId` — accountId, который был у карточки `(uid, broker)`
 * на момент открытия (для пересоздания в `SaveCardSettingsUseCase`).
 * `null` — карточки не было или accountId не выбран.
 */
data class OpenCardDialogResult(
    val broker: BrokerName,
    val accounts: List<BrokerAccount>,
    val selectedAccountId: String?,
    val oldAccountId: String?,
    val takenAccountIds: Set<String>,
)

/**
 * Результат [OpenCardDialogUseCase.execute].
 *
 * **Почему не `AppResult`:** `Failure` должен нести [broker] — иначе
 * VM не может выставить `selectedBroker` 1:1 (см. `openBrokerDialog`
 * в `OrderDialogsViewModel`: `selectedBroker = existingCard?.brokerName
 * ?: TINVEST` даже на failure).
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
 * **Composite-ключ (Фаза 1b):** `findCard(instrument, currentAccountId)`
 * — по полному `(uid, broker, accountId)` карточки слота. `currentAccountId`
 * — accountId, с которым карточка сейчас в слоте (может быть `null` —
 * карточка создана до выбора счёта). Возвращает `oldAccountId` в
 * [OpenCardDialogResult] — `existingCard?.accountId` (что было, чтобы
 * `SaveCardSettingsUseCase` удалил старое перед добавлением нового).
 *
 * **Failure из [LoadCardAccountsUseCase]** — `OpenCardDialogOutcome.Failure(broker, error)`,
 * не `AppResult.Failure`: [broker] нужен VM для 1:1 `selectedBroker`.
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

        suspend fun execute(
            instrument: Instrument,
            currentAccountId: String?,
        ): OpenCardDialogOutcome {
            logger.d(
                TAG,
                "execute: uid=${instrument.tscalpInstrumentId}, " +
                    "broker=${instrument.brokerName}, currentAccountId=$currentAccountId",
            )
            return try {
                val identities = cardsRepository.identities.first()
                val existingCard = identities.findCard(instrument, currentAccountId)
                val takenAccountIds =
                    identities
                        .filter { it.tscalpInstrumentId == instrument.tscalpInstrumentId && it.brokerName == instrument.brokerName }
                        .mapNotNull { it.accountId }
                        .filter { it != currentAccountId }
                        .toSet()
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
                                oldAccountId = existingCard?.accountId,
                                takenAccountIds = takenAccountIds,
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
