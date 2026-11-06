package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Загрузка счетов для диалога карточки (`openBrokerDialog` /
 * `loadDialogAccounts`) + выбор счёта: `savedAccountId`, если он есть
 * в списке (по `trim`), иначе — первый, иначе `null`.
 *
 * Вынесено из `OrdersViewModel.openBrokerDialog` /
 * `loadDialogAccounts` (clean-audit M013, M2): getAccounts + выбор
 * счёта — domain-orchestration, не presentation.
 *
 * **Не путать с [LoadBrokerAccountsUseCase]** (M006, settings-panel).
 * Семантика намеренно разная (Q10/Q11, 2026-11-07):
 * - [LoadBrokerAccountsUseCase]: читает `savedAccountId` **из
 *   DataStore**, при пустом — **сохраняет** first. Контракт
 *   settings-panel: «default-счёт в настройках».
 * - этот usecase: `savedAccountId` — **параметр** (per-card или
 *   `UiState`), **без** DataStore-чтения и **без** сохранения.
 *   Контракт диалога карточки: «счёт для конкретной карточки».
 *
 * Reuse M006 для диалога карточки дал бы:
 * - `savedAccountId` из `existingCard?.accountId` не передать
 *   (M006 читает DataStore, а не `UiState`);
 * - побочный `saveDefaultAccountId(first)` — поведение, которого
 *   у диалога карточки не было.
 *
 * **Failure:** VM очищает `dialogAccounts` и
 * `selectedAccountIdDialog` (общее поведение для обоих callsites).
 */
@Singleton
class LoadCardAccountsUseCase
    @Inject
    constructor(
        private val accountsRepository: AccountsRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "LoadCardAccountsUseCase"
        }

        /**
         * Загружает счета [brokerName] и выбирает счёт по
         * [savedAccountId] (validated by trim) или первый.
         *
         * @param savedAccountId — кандидат (per-card / из `UiState`);
         *   `null` — fallback на первый счёт.
         * @return `AppResult.Success(CardAccountsResult)` — accounts +
         *   `selectedAccountId` (`null` только при пустом accounts);
         *   `AppResult.Failure` — ошибка getAccounts / общая ошибка.
         */
        suspend fun execute(
            brokerName: BrokerName,
            sandboxMode: Boolean,
            savedAccountId: String?,
        ): AppResult<CardAccountsResult> {
            logger.d(
                TAG,
                "execute: brokerName=$brokerName, sandboxMode=$sandboxMode, " +
                    "savedAccountId=${savedAccountId?.let { "***" }}",
            )
            return try {
                val accountsResult = accountsRepository.getAccountsResult(brokerName, sandboxMode)
                when (accountsResult) {
                    is AppResult.Success -> {
                        val accounts = accountsResult.data
                        val trimmedSaved = savedAccountId?.trim()?.takeIf { it.isNotEmpty() }
                        val selectedId =
                            if (trimmedSaved != null && accounts.any { it.id.trim() == trimmedSaved }) {
                                accounts.first { it.id.trim() == trimmedSaved }.id
                            } else {
                                accounts.firstOrNull()?.id
                            }
                        AppResult.Success(
                            CardAccountsResult(
                                accounts = accounts,
                                selectedAccountId = selectedId,
                            ),
                        )
                    }
                    is AppResult.Failure -> {
                        logger.e(
                            TAG,
                            "getAccountsResult failed: ${accountsResult.error.message}",
                            accountsResult.error.cause,
                        )
                        accountsResult
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "load card accounts exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка загрузки счетов: ${e.message}", e))
            }
        }
    }

/**
 * Результат [LoadCardAccountsUseCase]: счета + выбранный счёт.
 * `selectedAccountId = null` только при пустом списке accounts.
 */
data class CardAccountsResult(
    val accounts: List<BrokerAccount>,
    val selectedAccountId: String?,
)
