package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.managers.PositionFlowManager
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.InstrumentRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.MarketDataRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AccountKey
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.SelectedInstrumentInfo
import com.gitlab.biomorf.tscalp.domain.models.findCard
import com.gitlab.biomorf.tscalp.domain.models.matches
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Выбор инструмента (основного) + обогащение карточки
 * «Последние просмотренные» — разбиение `OrdersViewModel` на
 * 4 VM, шаг 6.
 *
 * Вынесено из `OrdersViewModel.onInstrumentSelected` (clean-audit
 * M013, M2): getInstrument + lastPrice + portfolio position +
 * existingCard + save identity + save selected to session —
 * domain-orchestration, не presentation.
 *
 * **Не в scope** (VM-часть, 1:1): `_uiState.update`
 * (`selectedInstrument` / `ticker` / `searchQuery` / `searchResults`
 * / `isPriceLoading` / `currentPrice` / `currentPointValue` /
 * `lastSelectedInstruments`), `updateTradeDetails` /
 * `updatePairExecDetails`, `syncPriceInterest` /
 * `startPositionUpdates`, `saveState`.
 *
 * **Persistence:** `CardsRepository.save` — identity-only
 * (`CardIdentity`, не полная `SelectedInstrumentInfo`).
 * Полная `SelectedInstrumentInfo` (currentPrice, quantity,
 * profit, profitPercent) — enrich на момент выбора; при
 * следующем `sheet` emit Coord обновит её из
 * `PositionFlowManager.sheet` (Пробел 3).
 *
 * `pointValue = actualInstrument.moneyPerPointOfPrice` — из
 * «свежего» `Instrument` (может отличаться от переданного
 * `instrument`), 1:1 `OrdersViewModel.onInstrumentSelected`.
 *
 * `accountId` — из `AccountSelectionRepository.selected(TINVEST)`
 * (Q: источник per-card settings). `null` → позиция не ищется
 * (`quantity = 0L`, 1:1).
 */
@Singleton
class SelectInstrumentUseCase
    @Inject
    constructor(
        private val instrumentRepository: InstrumentRepository,
        private val marketDataRepository: MarketDataRepository,
        private val positionFlowManager: PositionFlowManager,
        private val cardsRepository: CardsRepository,
        private val accountSelectionRepository: AccountSelectionRepository,
        private val tradingStateRepository: TradingStateRepository,
        private val logger: Logger,
    ) {
        companion object {
            private const val TAG = "SelectInstrumentUseCase"
            private const val CARD_LIMIT = 5
        }

        /**
         * @return `AppResult.Success(SelectInstrumentResult)` с
         *   enriched card + pointValue; `AppResult.Failure` на
         *   исключении DataStore / repository.
         *   `getLastPrices` failure — не ошибка (price = null, 1:1).
         */
        suspend fun execute(instrument: Instrument): AppResult<SelectInstrumentResult> {
            logger.d(TAG, "execute: uid=${instrument.tscalpInstrumentId}, broker=${instrument.brokerName}")
            return try {
                val actualInstrument =
                    instrumentRepository.getInstrument(instrument.tscalpInstrumentId) ?: instrument
                val currentCards = cardsRepository.identities.first()
                val existingCard = currentCards.findCard(instrument)
                persistIdentity(instrument, currentCards, existingCard)
                saveSelectedToSession(instrument.tscalpInstrumentId)
                val price = fetchPrice(instrument.tscalpInstrumentId)
                val accountId = accountSelectionRepository.selected(BrokerName.TINVEST).first()
                val portfolioPos =
                    findPortfolioPosition(instrument, existingCard?.accountId ?: accountId)
                val card =
                    buildCard(
                        instrument = instrument,
                        price = price,
                        portfolioPos = portfolioPos,
                        existingCard = existingCard,
                    )
                AppResult.Success(
                    SelectInstrumentResult(
                        card = card,
                        pointValue = actualInstrument.moneyPerPointOfPrice,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "select instrument exception", e)
                AppResult.Failure(AppError.Unknown("Ошибка выбора инструмента: ${e.message}", e))
            }
        }

        private suspend fun fetchPrice(tscalpInstrumentId: String): Double? =
            when (
                val pricesResult =
                    marketDataRepository.getLastPricesResult(listOf(tscalpInstrumentId))
            ) {
                is AppResult.Success -> pricesResult.data[tscalpInstrumentId]
                is AppResult.Failure -> {
                    logger.w(
                        TAG,
                        "getLastPrices failed: ${pricesResult.error.message}",
                        pricesResult.error.cause,
                    )
                    null
                }
            }

        private fun findPortfolioPosition(
            instrument: Instrument,
            accountId: String?,
        ): Position? =
            accountId?.let {
                positionFlowManager.sheet.value
                    .accounts[AccountKey(BrokerName.TINVEST, it)]
                    ?.positions
                    ?.find { p ->
                        p.instrument.tscalpInstrumentId == instrument.tscalpInstrumentId &&
                            p.instrument.brokerName == instrument.brokerName
                    }
            }

        private suspend fun persistIdentity(
            instrument: Instrument,
            currentCards: List<CardIdentity>,
            existingCard: CardIdentity?,
        ) {
            val newIdentity =
                CardIdentity(
                    tscalpInstrumentId = instrument.tscalpInstrumentId,
                    ticker = instrument.ticker,
                    brokerName = instrument.brokerName,
                    accountId = existingCard?.accountId,
                )
            val updatedIdentities =
                (listOf(newIdentity) + currentCards.filter { !it.matches(instrument) })
                    .take(CARD_LIMIT)
            cardsRepository.save(updatedIdentities)
        }

        private suspend fun saveSelectedToSession(tscalpInstrumentId: String) {
            val snapshot = tradingStateRepository.state.value ?: tradingStateRepository.load()
            tradingStateRepository.save(
                snapshot.copy(selectedTscalpInstrumentId = tscalpInstrumentId),
            )
        }

        private fun buildCard(
            instrument: Instrument,
            price: Double?,
            portfolioPos: Position?,
            existingCard: CardIdentity?,
        ): SelectedInstrumentInfo =
            SelectedInstrumentInfo(
                instrument = instrument,
                currentPrice = price,
                priceChangePercent = null,
                quantity = portfolioPos?.quantity?.toLong() ?: 0L,
                averagePrice = portfolioPos?.currentPrice,
                profit = portfolioPos?.profit,
                profitPercent = portfolioPos?.profitPercent,
                brokerName = instrument.brokerName,
                accountId = existingCard?.accountId,
            )
    }

/**
 * Результат [SelectInstrumentUseCase]: enriched card + pointValue
 * «свежего» `Instrument` (для `currentPointValue` в VM).
 */
data class SelectInstrumentResult(
    val card: SelectedInstrumentInfo,
    val pointValue: Double,
)
