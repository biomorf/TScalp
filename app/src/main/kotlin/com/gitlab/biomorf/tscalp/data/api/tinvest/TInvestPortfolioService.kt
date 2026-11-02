package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
import com.gitlab.biomorf.tscalp.domain.models.toPosition
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
    ): AccountPortfolio =
        withContext(Dispatchers.IO) {
            val response = getPortfolio(accountId, sandboxMode)

            val positions = mutableListOf<Position>()

            response.positionsList.forEach { pos ->
                val uid = pos.instrumentUid
                if (uid.isBlank()) return@forEach

                val protoInstrument = instrumentsService.fetchProtoInstrument(uid)
                var marginAmount: Double? = null
                if (protoInstrument?.instrumentType == "futures" && protoInstrument.figi != null) {
                    marginAmount = instrumentsService.getFuturesMargin(protoInstrument.figi)
                }

                val instrument =
                    protoInstrument?.let {
                        TInvestInstrumentMapper.mapProtoToDomain(it, marginAmount)
                    }

                val quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L
                val currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0
                val expectedYield = pos.expectedYield?.let { it.units + it.nano / 1_000_000_000.0 }
                val avgPrice = pos.averagePositionPrice?.let { it.units + it.nano / 1_000_000_000.0 }

                val item =
                    PositionStreamItem(
                        tscalpInstrumentId = instrument?.tscalpInstrumentId ?: "",
                        brokerName = BrokerName.TINVEST,
                        ticker = instrument?.ticker ?: "",
                        classCode = instrument?.classCode ?: "",
                        isin = instrument?.isin ?: "",
                        quantity = quantity,
                        currentPrice = currentPrice,
                        averagePositionPrice = avgPrice,
                        expectedYield = expectedYield,
                        instrumentType = instrument?.instrumentType ?: InstrumentType.OTHER,
                        moneyPerPointOfPrice = instrument?.moneyPerPointOfPrice,
                        currency = instrument?.currency,
                        isDomesticCurrency =
                            instrument?.currency?.equals(BrokerName.TINVEST.domesticCurrency, ignoreCase = true) == true,
                        accountId = accountId,
                    )
                item.toPosition(instrument)?.let { positions += it }
                // Balance (money/blocked) в GetPortfolio отсутствует —
                // WS или GetPositions. См. задачу «Position: источники ...».
            }

            AccountPortfolio(
                accountId = accountId,
                brokerName = BrokerName.TINVEST,
                positions = positions,
                balances = emptyList(),
            )
        }
}
