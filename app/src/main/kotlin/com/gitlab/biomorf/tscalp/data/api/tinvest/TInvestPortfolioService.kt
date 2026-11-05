package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.AccountPortfolio
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.Position
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.moneyPerPointOfPrice
import com.gitlab.biomorf.tscalp.domain.models.toPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.PortfolioPosition
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

    suspend fun fetchPortfolioPositions(
        accountId: String,
        sandboxMode: Boolean,
    ): AccountPortfolio =
        withContext(Dispatchers.IO) {
            val response = getPortfolio(accountId, sandboxMode)

            val positions =
                response.positionsList.mapNotNull { pos ->
                    enrichWithInstrumentData(pos, accountId)
                }

            // Balance (money/blocked) в GetPortfolio отсутствует —
            // WS или GetPositions. См. задачу «Position: источники ...».
            AccountPortfolio(
                accountId = accountId,
                brokerName = BrokerName.TINVEST,
                positions = positions,
                balances = emptyList(),
            )
        }

    private suspend fun enrichWithInstrumentData(
        pos: PortfolioPosition,
        accountId: String,
    ): Position? {
        val uid = pos.instrumentUid
        if (uid.isBlank()) return null

        val protoInstrument = instrumentsService.fetchProtoInstrument(uid)
        val marginAmount =
            if (protoInstrument?.instrumentType == "futures" && protoInstrument.figi != null) {
                instrumentsService.getFuturesMargin(protoInstrument.figi)
            } else {
                null
            }

        val instrument =
            protoInstrument?.let {
                TInvestInstrumentMapper.mapProtoToDomain(it, marginAmount)
            }

        val item = enrichWithMarginData(pos, instrument, accountId)
        return item.toPosition(instrument)
    }

    private fun enrichWithMarginData(
        pos: PortfolioPosition,
        instrument: Instrument?,
        accountId: String,
    ): PositionStreamItem =
        PositionStreamItem(
            tscalpInstrumentId = instrument?.tscalpInstrumentId ?: "",
            brokerName = BrokerName.TINVEST,
            ticker = instrument?.ticker ?: "",
            classCode = instrument?.classCode ?: "",
            isin = instrument?.isin ?: "",
            quantity = pos.quantity?.let { it.units + it.nano / 1_000_000_000.0 }?.toLong() ?: 0L,
            currentPrice = pos.currentPrice?.let { it.units + it.nano / 1_000_000_000.0 } ?: 0.0,
            averagePositionPrice = pos.averagePositionPrice?.let { it.units + it.nano / 1_000_000_000.0 },
            expectedYield = pos.expectedYield?.let { it.units + it.nano / 1_000_000_000.0 },
            instrumentType = instrument?.instrumentType ?: InstrumentType.OTHER,
            moneyPerPointOfPrice = instrument?.moneyPerPointOfPrice,
            currency = instrument?.currency,
            isDomesticCurrency =
                instrument?.currency?.equals(BrokerName.TINVEST.domesticCurrency, ignoreCase = true) == true,
            accountId = accountId,
        )
}
