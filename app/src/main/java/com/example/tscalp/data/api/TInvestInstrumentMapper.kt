package com.example.tscalp.data.api

import com.example.tscalp.domain.models.FutureUi
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.ShareUi
import ru.tinkoff.piapi.contract.v1.Instrument

/**
 * Преобразование protobuf-инструмента T-Invest в доменную модель.
 */
/**
 * Преобразует protobuf‑объект Instrument в универсальный InstrumentUi.
 * tscalpInstrumentId заполняется из uid (рекомендованный идентификатор Т‑Инвестиций).
 * Для фьючерсов возвращает FutureUi, для акций – ShareUi, для остальных – базовый InstrumentUi.
 */
object TInvestInstrumentMapper {

    /**
     * @param instrument             protobuf-объект из T-Invest SDK
     * @param minPriceIncrementAmountOverride стоимость пункта для фьючерсов,
     *        полученная из getFuturesMargin. Для не-фьючерсов игнорируется.
     */
    fun mapProtoToDomain(
        instrument: Instrument,
        minPriceIncrementAmountOverride: Double? = null
    ): InstrumentUi {
        val uid = instrument.uid
        val figi = instrument.figi ?: ""
        val type = instrument.instrumentType ?: ""
        val minInc = TInvestConverters.quotationToDouble(instrument.minPriceIncrement)
        val tradingStatus = instrument.tradingStatus.name

        return when (type) {
            "futures" -> FutureUi(
                tscalpInstrumentId = uid,
                ticker = instrument.ticker,
                classCode = instrument.classCode ?: "",
                isin = instrument.isin ?: "",
                ttech_uid = uid,
                ttech_figi = figi,
                name = instrument.name,
                currency = instrument.currency,
                lot = instrument.lot,
                exchange = instrument.exchange,
                tradingStatus = tradingStatus,
                apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
                buyAvailableFlag = instrument.buyAvailableFlag,
                sellAvailableFlag = instrument.sellAvailableFlag,
                shortEnabledFlag = instrument.shortEnabledFlag,
                minPriceIncrement = minInc,
                minPriceIncrementAmount = minPriceIncrementAmountOverride,
                klong = null, kshort = null, dlong = null, dshort = null,
                dlongMin = null, dshortMin = null,
                first1minCandleDate = null,
                first1dayCandleDate = null,
                forIisFlag = instrument.forIisFlag,
                forQualInvestorFlag = instrument.forQualInvestorFlag,
                weekendFlag = instrument.weekendFlag,
                blockedTcaFlag = instrument.blockedTcaFlag,
                countryOfRisk = instrument.countryOfRisk,
                countryOfRiskName = instrument.countryOfRiskName,
                sector = null,
                brand = null,
                requiredTests = null,
                expirationDate = null,
                firstTradeDate = null,
                lastTradeDate = null,
                futuresType = null,
                assetType = null,
                basicAsset = null,
                basicAssetSize = null,
                positionUid = instrument.positionUid,
                basicAssetPositionUid = null,
                initialMarginOnBuy = null,
                initialMarginOnSell = null,
                dlongClient = null,
                dshortClient = null
            )

            "share" -> ShareUi(
                tscalpInstrumentId = uid,
                ticker = instrument.ticker,
                classCode = instrument.classCode ?: "",
                isin = instrument.isin ?: "",
                ttech_uid = uid,
                ttech_figi = figi,
                name = instrument.name,
                currency = instrument.currency,
                lot = instrument.lot,
                exchange = instrument.exchange,
                tradingStatus = tradingStatus,
                apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
                buyAvailableFlag = instrument.buyAvailableFlag,
                sellAvailableFlag = instrument.sellAvailableFlag,
                shortEnabledFlag = instrument.shortEnabledFlag,
                minPriceIncrement = minInc,
                minPriceIncrementAmount = null,
                klong = null, kshort = null, dlong = null, dshort = null,
                dlongMin = null, dshortMin = null,
                first1minCandleDate = null,
                first1dayCandleDate = null,
                forIisFlag = instrument.forIisFlag,
                forQualInvestorFlag = instrument.forQualInvestorFlag,
                weekendFlag = instrument.weekendFlag,
                blockedTcaFlag = instrument.blockedTcaFlag,
                countryOfRisk = instrument.countryOfRisk,
                countryOfRiskName = instrument.countryOfRiskName,
                sector = null,
                brand = null,
                requiredTests = null,
                ipoDate = null,
                issueSize = null,
                issueSizePlan = null,
                nominal = null,
                divYieldFlag = null,
                shareType = null,
                liquidityFlag = null,
                assetUid = null,
                instrumentExchange = null
            )

            else -> InstrumentUi(
                tscalpInstrumentId = uid,
                ticker = instrument.ticker,
                classCode = instrument.classCode ?: "",
                isin = instrument.isin ?: "",
                ttech_uid = uid,
                ttech_figi = figi,
                name = instrument.name,
                currency = instrument.currency,
                lot = instrument.lot,
                instrumentType = type
            )
        }
    }
}