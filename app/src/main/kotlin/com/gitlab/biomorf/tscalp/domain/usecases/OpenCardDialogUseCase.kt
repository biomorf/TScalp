package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат [OpenCardDialogUseCase]: ticker + broker + accounts +
 * selectedAccountId — всё, что нужно `OrderDialogsState` для
 * открытия диалога карточки.
 */
data class OpenCardDialogResult(
    val ticker: String,
    val broker: BrokerName,
    val accounts: List<BrokerAccount>,
    val selectedAccountId: String?,
)

/**
 * Открытие диалога настроек карточки (брокер + счёт) — clean-audit
 * **M015** (open, M2 partial). Вынесено из
 * `OrderDialogsViewModel.openBrokerDialog` (findCard + selected
 * fallback + `LoadCardAccountsUseCase.execute`).
 *
 * **Не путать с [LoadCardAccountsUseCase]:** тот делает только
 * getAccounts + savedFallback; здесь — findCard по ticker из
 * `CardsRepository.identities` + selected fallback из
 * `AccountSelectionRepository.selected(TINVEST)` + `sandboxMode`
 * из `SandboxPreferencesRepository` + делегирование в
 * `LoadCardAccountsUseCase`.
 *
 * **Не путать с `loadDialogAccounts` (VM):** тот — thin delegate
 * на `LoadCardAccountsUseCase` (M2-fixed in 12a, не M2).
 *
 * **Failure из [LoadCardAccountsUseCase]** — passthrough
 * `AppResult.Failure`; VM разбирает 1:1 (accounts = empty,
 * selectedAccountId = null).
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

        suspend fun execute(ticker: String): AppResult<OpenCardDialogResult> {
            logger.d(TAG, "execute: ticker=$ticker")
            return try {
                val existingCard =
                    cardsRepository.identities.first().find { it.ticker == ticker }
                val broker = existingCard?.brokerName ?: BrokerName.TINVEST
                val savedAccountId =
                    existingCard?.accountId
                        ?: accountSelectionRepository.selected(BrokerName.TINVEST).first()

                when (
                    val result =
                        loadCardAccountsUseCase.execute(
                            brokerName = broker,
                            sandboxMode = sandboxPreferencesRepository.isSandboxMode(),
                            savedAccountId = savedAccountId,
                        )
                ) {
                    is AppResult.Success ->
                        AppResult.Success(
                            OpenCardDialogResult(
                                ticker = ticker,
                                broker = broker,
                                accounts = result.data.accounts,
                                selectedAccountId = result.data.selectedAccountId,
                            ),
                        )
                    is AppResult.Failure -> {
                        logger.e(
                            TAG,
                            "loadCardAccounts failed: ticker=$ticker, " +
                                "broker=${broker.displayName}: ${result.error.message}",
                            result.error.cause,
                        )
                        result
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "open card dialog exception: ticker=$ticker", e)
                AppResult.Failure(
                    AppError.Unknown("Ошибка открытия диалога карточки: ${e.message}", e),
                )
            }
        }
    }
