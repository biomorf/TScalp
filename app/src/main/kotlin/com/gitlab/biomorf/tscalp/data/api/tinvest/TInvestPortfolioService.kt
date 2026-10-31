package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.FutureUi
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.PortfolioRequest
import ru.tinkoff.piapi.contract.v1.PortfolioResponse

/**
 * Портфель T-Invest: снимок позиций и низкоуровневый portfolio-запрос.
 *
 * Выделен из `TInvestMarketDataService` (clean-audit Н63, шаг 4).
 * Отвечает за: портфель, обогащение позиций данными инструментов.
 * Зависит от [TInvestInstrumentsService] (направление `portfolio -> instruments`).
 */
class TInvestPortfolioService(
    private val state: TInvestApiState,
    private val instrumentsService: TInvestInstrumentsService,
) {
    companion object {
        private const val TAG = "TInvestPortfolio"
    }

    private suspend fun getPortfolio(
        accountId: String,
        sandboxMode: Boolean,
    ): PortfolioResponse =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = PortfolioRequest.newBuilder().setAccountId(accountId).build()
            if (sandboxMode) {
                currentApi.sandboxServiceSync.getSandboxPortfolio(request)
            } else {
                currentApi.operationsServiceSync.getPortfolio(request)
            }
        }

    // Архитектурный долг: функция требует декомпозиции.
    // См. ROADMAP → «Рефакторинг data-слоя T-Invest».
    @Suppress("CyclomaticComplexMethod")
    suspend fun fetchPortfolioPositions(
        accountId: String,
        sandboxMode: Boolean,
    ): List<PositionStreamItem> =
        withContext(Dispatchers.IO) {
            val response = getPortfolio(accountId, sandboxMode)

            response.positionsList.mapNotNull { pos ->
                val uid = pos.instrumentUid
                if (uid.isBlank()) return@mapNotNull null

                val protoInstrument = instrumentsService.fetchProtoInstrument(uid)
                var marginAmount: Double? = null
                if (protoInstrument?.instrumentType == "futures" && protoInstrument.figi != null) {
                    marginAmount = instrumentsService.getFuturesMargin(protoInstrument.figi)
                }

                val instrumentUi =
                    protoInstrument?.let {
                        TInvestInstrumentMapper.mapProtoToDomain(it, marginAmount)
                    }
                val pointVal = (instrumentUi as? FutureUi)?.pointValue

                val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
                val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                val expectedYield = pos.expectedYield?.let { it.units + it.nano / 1_000_000_000.0 }
                val avgPrice = pos.averagePositionPrice?.let { it.units + it.nano / 1_000_000_000.0 }

                PositionStreamItem(
                    tscalpInstrumentId = instrumentUi?.tscalpInstrumentId ?: "",
                    brokerName = BrokerName.TINVEST,
                    ticker = instrumentUi?.ticker ?: "",
                    classCode = instrumentUi?.classCode ?: "",
                    isin = instrumentUi?.isin ?: "",
                    quantity = quantity,
                    currentPrice = currentPrice,
                    averagePositionPrice = avgPrice,
                    expectedYield = expectedYield,
                    instrumentType = instrumentUi?.instrumentType ?: "",
                    pointValue = pointVal,
                    currency = instrumentUi?.currency,
                )
            }
        }
}
