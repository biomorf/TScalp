package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.Future
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.Share
import ru.tinkoff.piapi.contract.v1.Instrument as ProtoInstrument

/**
 * Преобразует protobuf‑объект Instrument в доменный универсальный Instrument.
 * tscalpInstrumentId заполняется из uid (рекомендованный идентификатор Т‑Инвестиций).
 * Для фьючерсов возвращает Future, для акций – Share, для остальных – базовый Instrument.
 */
object TInvestInstrumentMapper {
    /**
     * Архитектурный долг: функция требует декомпозиции.
     * См. ROADMAP → «Рефакторинг data-слоя T-Invest».
     *
     * @param instrument             protobuf-объект из T-Invest SDK
     * @param minPriceIncrementAmountOverride стоимость пункта для фьючерсов,
     *        полученная из getFuturesMargin. Для не-фьючерсов игнорируется.
     */
    @Suppress("LongMethod")
    fun mapProtoToDomain(
        instrument: ProtoInstrument,
        minPriceIncrementAmountOverride: Double? = null,
    ): Instrument {
        val brokerUid = instrument.uid
        val figi = instrument.figi?.takeIf { it.isNotBlank() }
        val type = InstrumentType.fromBrokerString(instrument.instrumentType ?: "")
        val minInc = TInvestConverters.quotationToDouble(instrument.minPriceIncrement)
        val tradingStatus = instrument.tradingStatus.name

        return when (type) {
            InstrumentType.FUTURES ->
                Future(
                    tscalpInstrumentId = brokerUid,
                    ticker = instrument.ticker,
                    classCode = instrument.classCode?.takeIf { it.isNotBlank() },
                    isin = instrument.isin ?: "",
                    figi = figi,
                    name = instrument.name,
                    currency = instrument.currency.takeIf { it.isNotBlank() },
                    lot = instrument.lot.toLong(),
                    exchange = instrument.exchange,
                    tradingStatus = tradingStatus,
                    apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
                    buyAvailableFlag = instrument.buyAvailableFlag,
                    sellAvailableFlag = instrument.sellAvailableFlag,
                    shortEnabledFlag = instrument.shortEnabledFlag,
                    minPriceIncrement = minInc,
                    minPriceIncrementAmount = minPriceIncrementAmountOverride,
                    klong = null,
                    kshort = null,
                    dlong = null,
                    dshort = null,
                    dlongMin = null,
                    dshortMin = null,
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
                    dshortClient = null,
                )

            InstrumentType.SHARE ->
                Share(
                    tscalpInstrumentId = brokerUid,
                    ticker = instrument.ticker,
                    classCode = instrument.classCode?.takeIf { it.isNotBlank() },
                    isin = instrument.isin ?: "",
                    figi = figi,
                    name = instrument.name,
                    currency = instrument.currency.takeIf { it.isNotBlank() },
                    lot = instrument.lot.toLong(),
                    exchange = instrument.exchange,
                    tradingStatus = tradingStatus,
                    apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
                    buyAvailableFlag = instrument.buyAvailableFlag,
                    sellAvailableFlag = instrument.sellAvailableFlag,
                    shortEnabledFlag = instrument.shortEnabledFlag,
                    minPriceIncrement = minInc,
                    minPriceIncrementAmount = null,
                    klong = null,
                    kshort = null,
                    dlong = null,
                    dshort = null,
                    dlongMin = null,
                    dshortMin = null,
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
                    instrumentExchange = null,
                )

            else ->
                Instrument(
                    tscalpInstrumentId = brokerUid,
                    ticker = instrument.ticker,
                    classCode = instrument.classCode?.takeIf { it.isNotBlank() },
                    isin = instrument.isin ?: "",
                    figi = figi,
                    name = instrument.name,
                    currency = instrument.currency.takeIf { it.isNotBlank() },
                    lot = instrument.lot.toLong(),
                    instrumentType = type,
                )
        }
    }
}
