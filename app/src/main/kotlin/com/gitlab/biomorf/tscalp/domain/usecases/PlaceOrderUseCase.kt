package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.PortfolioRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.StopOrderExpirationType
import com.gitlab.biomorf.tscalp.domain.models.matches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Параметры торговой операции, которые Form-VM получает из
 * `OrderFormState` и передаёт в [PlaceOrderUseCase].
 *
 * Только «сырые» Form-значения (`instrument` / `quantity` /
 * `orderType` / `limitPrice` / `stopPrice` / pair). Координатные
 * данные (`activeCard` из `CardsRepository.identities`, `accountId`
 * из `AccountSelectionRepository.selected`) резолвятся **внутри
 * usecase** — не через cross-VM параметры.
 *
 * `limitPrice` / `stopPrice` — `String` (как в `OrderFormState`):
 * пустая строка = «не задано», 1:1 с текущим `buildOrderInput`
 * (`PostOrderInput.limitPrice: String?` принимает `""`).
 */
data class PlaceOrderRequest(
    val instrument: Instrument,
    /** accountId слота primary (какой счёт у карточки в момент postOrder). */
    val selectedAccountId: String?,
    val quantity: Long,
    val orderType: OrderTypeSelection,
    val limitPrice: String,
    val stopPrice: String,
    val expirationType: StopOrderExpirationType,
    val pairedInstrument: Instrument?,
    /** accountId слота pair (null — parный счёт совпадает с primary). */
    val pairedAccountId: String?,
    val pairedMultiplier: Long,
)

/**
 * Результат [PlaceOrderUseCase]: `PostOrderOutcome` + `freeBalance`.
 *
 * `freeBalance` — свободный остаток по счёту после успешной
 * заявки (`PortfolioRepository.getBalanceResult`); `null` —
 * getBalance failure (не блокирует успех, 1:1 с Coord).
 */
sealed interface PlaceOrderOutcome {
    data class Success(
        val primary: PrimaryOutcome,
        val paired: PairedOutcome?,
        val freeBalance: Double?,
    ) : PlaceOrderOutcome

    data class InsufficientFunds(
        val balance: Double,
        val required: Double,
    ) : PlaceOrderOutcome

    data class Error(
        val message: String,
        val cause: Throwable?,
    ) : PlaceOrderOutcome

    data class InvalidRequest(
        val reason: String,
    ) : PlaceOrderOutcome
}

/**
 * Торговая операция: резолв карточки/счёта из domain + `PostOrderUseCase`
 * + `freeBalance`. Заменяет `OrdersViewModel.postOrder` /
 * `buildOrderInput` / `handlePostOrderSuccess` orchestration
 * (clean-audit M013, M2).
 *
 * **Принцип централизации (2026-11-07):** все cross-VM данные
 * (`activeCard`, `accountId`, `sandboxMode`) резолвятся **внутри
 * usecase** из domain-репозиториев. Form-VM передаёт только Form-поля
 * и `OrderDirection` (user intent). Никаких параметров
 * `defaultAccountId` / `cards` / cross-VM call'ов.
 *
 * **Резолв:**
 * - `cards = CardsRepository.identities.first()`; `activeCard =
 *   cards.find { it.matches(request.instrument) }` — 1:1 с Coord
 *   `lastSelectedInstruments.find` (оба — projection of
 *   `CardsRepository.identities`, A3/A4-b-1).
 * - `accountId = activeCard?.accountId
 *   ?: AccountSelectionRepository.selected(TINVEST).first()`.
 *   Coord пишет туда же после `loadOrdersAccountsUseCase`
 *   (`accountSelectionRepository.select(TINVEST, data.selectedAccountId)`),
 *   поэтому эквивалентно Coord `selectedAccountId`. `null` →
 *   [PlaceOrderOutcome.InvalidRequest].
 * - `brokerName = activeCard?.brokerName ?: TINVEST`.
 * - `sandboxMode = SandboxPreferencesRepository.isSandboxMode()`.
 * - `postOrderUseCase.execute(PostOrderInput)` — 1:1 с Coord
 *   `buildOrderInput`.
 * - На `Success` — `PortfolioRepository.getBalanceResult(accountId)`
 *   → `freeBalance` (failure → `null`, лог W, 1:1).
 *
 * **Не делает** (presentation, Form-VM / `### M001`):
 * `formatPostOrderSuccess` / `handlePostOrder*` / `_state.update`
 * (`StatusEvent` / `freeBalance` / cleared fields).
 *
 * **`refreshLastSelectedInstruments` не вызывается** — Coord
 * реактивен через `PositionFlowManager.sheet` →
 * `updatePositionsFromSheet` → `refreshLastSelectedInstruments`
 * (2c-фиксация: cross-VM call запрещён; Coord — владелец
 * `lastSelectedInstruments`).
 *
 * Non-throwing (кроме `CancellationException` — rethrow).
 */
class PlaceOrderUseCase
    constructor(
        private val postOrderUseCase: PostOrderUseCase,
        private val cardsRepository: CardsRepository,
        private val accountSelectionRepository: AccountSelectionRepository,
        private val portfolioRepository: PortfolioRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "PlaceOrderUseCase"
        }

        suspend fun execute(
            request: PlaceOrderRequest,
            direction: OrderDirection,
        ): PlaceOrderOutcome {
            logger.d(
                TAG,
                "execute: ticker=${request.instrument.ticker}, direction=$direction, qty=${request.quantity}",
            )
            return try {
                val cards = cardsRepository.identities.first()
                val context = resolveContext(request, cards) ?: return invalidAccount()
                val input = buildInput(request, direction, cards, context)
                mapOutcome(postOrderUseCase.execute(input), context.accountId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "place order exception", e)
                PlaceOrderOutcome.Error(e.message ?: "Ошибка размещения заявки", e)
            }
        }

        private fun invalidAccount(): PlaceOrderOutcome = PlaceOrderOutcome.InvalidRequest("Не удалось определить счёт")

        private suspend fun resolveContext(
            request: PlaceOrderRequest,
            cards: List<CardIdentity>,
        ): ResolvedContext? {
            val activeCard = cards.find { it.matches(request.instrument, request.selectedAccountId) }
            val accountId =
                activeCard?.accountId
                    ?: request.selectedAccountId
                    ?: accountSelectionRepository.selected(request.instrument.brokerName).first()
                    ?: return null
            val context =
                ResolvedContext(
                    activeCard = activeCard,
                    accountId = accountId,
                )
            logger.d(TAG, "resolveContext: $context")
            return context
        }

        private fun buildInput(
            request: PlaceOrderRequest,
            direction: OrderDirection,
            cards: List<CardIdentity>,
            context: ResolvedContext,
        ): PostOrderInput {
            val pairedInput = buildPairedInput(request, cards, context.accountId)
            return PostOrderInput(
                instrument = request.instrument,
                quantity = request.quantity,
                direction = direction,
                accountId = context.accountId,
                orderType = request.orderType,
                limitPrice = request.limitPrice,
                stopPrice = request.stopPrice,
                expirationType = request.expirationType,
                paired = pairedInput,
            )
        }

        private fun buildPairedInput(
            request: PlaceOrderRequest,
            cards: List<CardIdentity>,
            primaryAccountId: String,
        ): PairedOrderInput? {
            val pairedInstrument = request.pairedInstrument ?: return null
            val pairedCard = cards.find { it.matches(pairedInstrument, request.pairedAccountId) }
            return PairedOrderInput(
                instrument = pairedInstrument,
                accountId = pairedCard?.accountId ?: request.pairedAccountId ?: primaryAccountId,
                multiplier = request.pairedMultiplier,
            )
        }

        private suspend fun mapOutcome(
            outcome: PostOrderOutcome,
            accountId: String,
        ): PlaceOrderOutcome =
            when (outcome) {
                is PostOrderOutcome.Success ->
                    PlaceOrderOutcome.Success(
                        primary = outcome.primary,
                        paired = outcome.paired,
                        freeBalance = fetchFreeBalance(accountId),
                    )
                is PostOrderOutcome.InsufficientFunds ->
                    PlaceOrderOutcome.InsufficientFunds(outcome.balance, outcome.required)
                is PostOrderOutcome.Error ->
                    PlaceOrderOutcome.Error(outcome.message, outcome.cause)
                is PostOrderOutcome.InvalidRequest ->
                    PlaceOrderOutcome.InvalidRequest(outcome.reason)
            }

        private suspend fun fetchFreeBalance(accountId: String): Double? =
            when (val balanceResult = portfolioRepository.getBalanceResult(accountId)) {
                is AppResult.Success -> balanceResult.data
                is AppResult.Failure -> {
                    logger.w(
                        TAG,
                        "getBalance failed: ${balanceResult.error.message}",
                        balanceResult.error.cause,
                    )
                    null
                }
            }
    }

private data class ResolvedContext(
    val activeCard: CardIdentity?,
    val accountId: String,
)
