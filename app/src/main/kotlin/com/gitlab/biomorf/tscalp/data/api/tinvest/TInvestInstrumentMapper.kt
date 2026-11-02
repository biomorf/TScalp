package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.Bond
import com.gitlab.biomorf.tscalp.domain.models.Currency
import com.gitlab.biomorf.tscalp.domain.models.Etf
import com.gitlab.biomorf.tscalp.domain.models.Future
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.Option
import com.gitlab.biomorf.tscalp.domain.models.Other
import com.gitlab.biomorf.tscalp.domain.models.Share
import ru.tinkoff.piapi.contract.v1.Instrument as ProtoInstrument

/**
 * Protobuf `Instrument` -> domain `Instrument` (sealed, Phase 2).
 *
 * 6 mappers + dispatcher (by [InstrumentType]). Each mapper reads
 * only its own fields; common fields are duplicated across mappers
 * but that's the price of ISP/LSP (see `docs/domain_models.md` §8).
 *
 * `Bond`/`Option`/`Etf`/`Currency` subtype-specific fields are null:
 * T-Invest SDK provides `Bond`/`Option` proto types, but bulk `Instrument`
 * doesn't carry those fields; per §8, расширение — при реализации BCS/Finam.
 */
object TInvestInstrumentMapper {
    /**
     * @param instrument protobuf-объект из T-Invest SDK
     * @param priceStepCostOverride стоимость пункта для фьючерсов,
     *        полученная из getFuturesMargin. Для не-фьючерсов игнорируется.
     */
    fun mapProtoToDomain(
        instrument: ProtoInstrument,
        priceStepCostOverride: Double? = null,
    ): Instrument {
        val type = InstrumentType.fromBrokerString(instrument.instrumentType ?: "")
        return when (type) {
            InstrumentType.FUTURES -> mapFuture(instrument, priceStepCostOverride)
            InstrumentType.SHARE -> mapShare(instrument)
            InstrumentType.BOND, InstrumentType.NOTE, InstrumentType.EURO_BOND -> mapBond(instrument)
            InstrumentType.OPTION -> mapOption(instrument)
            InstrumentType.ETF, InstrumentType.MUTUAL_FUND -> mapEtf(instrument)
            InstrumentType.CURRENCY -> mapCurrency(instrument)
            else -> mapOther(instrument, type)
        }
    }

    private fun mapFuture(instrument: ProtoInstrument, priceStepCostOverride: Double?): Future =
        Future(
            tscalpInstrumentId = instrument.uid,
            ticker = instrument.ticker,
            name = instrument.name,
            isin = instrument.isin ?: "",
            classCode = instrument.classCode?.takeIf { it.isNotBlank() },
            figi = instrument.figi?.takeIf { it.isNotBlank() },
            exchange = instrument.exchange,
            lot = instrument.lot.toLong(),
            currency = instrument.currency.takeIf { it.isNotBlank() },
            settlementCurrency = null,
            priceStepCost = priceStepCostOverride,
            minPriceIncrement = TInvestConverters.quotationToDouble(instrument.minPriceIncrement),
            expirationDate = null,
            apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
            buyAvailableFlag = instrument.buyAvailableFlag,
            sellAvailableFlag = instrument.sellAvailableFlag,
            shortEnabledFlag = instrument.shortEnabledFlag,
            forIisFlag = instrument.forIisFlag,
            forQualInvestorFlag = instrument.forQualInvestorFlag,
            weekendFlag = instrument.weekendFlag,
            blockedTcaFlag = instrument.blockedTcaFlag,
            instrumentType = InstrumentType.FUTURES,
        )

    private fun mapShare(instrument: ProtoInstrument): Share =
        Share(
            tscalpInstrumentId = instrument.uid,
            ticker = instrument.ticker,
            name = instrument.name,
            isin = instrument.isin ?: "",
            classCode = instrument.classCode?.takeIf { it.isNotBlank() },
            figi = instrument.figi?.takeIf { it.isNotBlank() },
            exchange = instrument.exchange,
            lot = instrument.lot.toLong(),
            currency = instrument.currency.takeIf { it.isNotBlank() },
            settlementCurrency = null,
            priceStepCost = null,
            minPriceIncrement = TInvestConverters.quotationToDouble(instrument.minPriceIncrement),
            expirationDate = null,
            apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
            buyAvailableFlag = instrument.buyAvailableFlag,
            sellAvailableFlag = instrument.sellAvailableFlag,
            shortEnabledFlag = instrument.shortEnabledFlag,
            forIisFlag = instrument.forIisFlag,
            forQualInvestorFlag = instrument.forQualInvestorFlag,
            weekendFlag = instrument.weekendFlag,
            blockedTcaFlag = instrument.blockedTcaFlag,
            instrumentType = InstrumentType.SHARE,
        )

    private fun mapBond(instrument: ProtoInstrument): Bond =
        Bond(
            tscalpInstrumentId = instrument.uid,
            ticker = instrument.ticker,
            name = instrument.name,
            isin = instrument.isin ?: "",
            classCode = instrument.classCode?.takeIf { it.isNotBlank() },
            figi = instrument.figi?.takeIf { it.isNotBlank() },
            exchange = instrument.exchange,
            lot = instrument.lot.toLong(),
            currency = instrument.currency.takeIf { it.isNotBlank() },
            settlementCurrency = null,
            priceStepCost = null,
            minPriceIncrement = TInvestConverters.quotationToDouble(instrument.minPriceIncrement),
            expirationDate = null,
            apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
            buyAvailableFlag = instrument.buyAvailableFlag,
            sellAvailableFlag = instrument.sellAvailableFlag,
            shortEnabledFlag = instrument.shortEnabledFlag,
            forIisFlag = instrument.forIisFlag,
            forQualInvestorFlag = instrument.forQualInvestorFlag,
            weekendFlag = instrument.weekendFlag,
            blockedTcaFlag = instrument.blockedTcaFlag,
            instrumentType = InstrumentType.BOND,
        )

    private fun mapOption(instrument: ProtoInstrument): Option =
        Option(
            tscalpInstrumentId = instrument.uid,
            ticker = instrument.ticker,
            name = instrument.name,
            isin = instrument.isin ?: "",
            classCode = instrument.classCode?.takeIf { it.isNotBlank() },
            figi = instrument.figi?.takeIf { it.isNotBlank() },
            exchange = instrument.exchange,
            lot = instrument.lot.toLong(),
            currency = instrument.currency.takeIf { it.isNotBlank() },
            settlementCurrency = null,
            priceStepCost = null,
            minPriceIncrement = TInvestConverters.quotationToDouble(instrument.minPriceIncrement),
            expirationDate = null,
            apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
            buyAvailableFlag = instrument.buyAvailableFlag,
            sellAvailableFlag = instrument.sellAvailableFlag,
            shortEnabledFlag = instrument.shortEnabledFlag,
            forIisFlag = instrument.forIisFlag,
            forQualInvestorFlag = instrument.forQualInvestorFlag,
            weekendFlag = instrument.weekendFlag,
            blockedTcaFlag = instrument.blockedTcaFlag,
            instrumentType = InstrumentType.OPTION,
        )

    private fun mapEtf(instrument: ProtoInstrument): Etf =
        Etf(
            tscalpInstrumentId = instrument.uid,
            ticker = instrument.ticker,
            name = instrument.name,
            isin = instrument.isin ?: "",
            classCode = instrument.classCode?.takeIf { it.isNotBlank() },
            figi = instrument.figi?.takeIf { it.isNotBlank() },
            exchange = instrument.exchange,
            lot = instrument.lot.toLong(),
            currency = instrument.currency.takeIf { it.isNotBlank() },
            settlementCurrency = null,
            priceStepCost = null,
            minPriceIncrement = TInvestConverters.quotationToDouble(instrument.minPriceIncrement),
            expirationDate = null,
            apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
            buyAvailableFlag = instrument.buyAvailableFlag,
            sellAvailableFlag = instrument.sellAvailableFlag,
            shortEnabledFlag = instrument.shortEnabledFlag,
            forIisFlag = instrument.forIisFlag,
            forQualInvestorFlag = instrument.forQualInvestorFlag,
            weekendFlag = instrument.weekendFlag,
            blockedTcaFlag = instrument.blockedTcaFlag,
            instrumentType = InstrumentType.ETF,
        )

    private fun mapCurrency(instrument: ProtoInstrument): Currency =
        Currency(
            tscalpInstrumentId = instrument.uid,
            ticker = instrument.ticker,
            name = instrument.name,
            isin = instrument.isin ?: "",
            classCode = instrument.classCode?.takeIf { it.isNotBlank() },
            figi = instrument.figi?.takeIf { it.isNotBlank() },
            exchange = instrument.exchange,
            lot = instrument.lot.toLong(),
            currency = instrument.currency.takeIf { it.isNotBlank() },
            settlementCurrency = null,
            priceStepCost = null,
            minPriceIncrement = TInvestConverters.quotationToDouble(instrument.minPriceIncrement),
            expirationDate = null,
            apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
            buyAvailableFlag = instrument.buyAvailableFlag,
            sellAvailableFlag = instrument.sellAvailableFlag,
            shortEnabledFlag = instrument.shortEnabledFlag,
            forIisFlag = instrument.forIisFlag,
            forQualInvestorFlag = instrument.forQualInvestorFlag,
            weekendFlag = instrument.weekendFlag,
            blockedTcaFlag = instrument.blockedTcaFlag,
            instrumentType = InstrumentType.CURRENCY,
        )

    private fun mapOther(instrument: ProtoInstrument, type: InstrumentType): Other =
        Other(
            tscalpInstrumentId = instrument.uid,
            ticker = instrument.ticker,
            name = instrument.name,
            isin = instrument.isin ?: "",
            classCode = instrument.classCode?.takeIf { it.isNotBlank() },
            figi = instrument.figi?.takeIf { it.isNotBlank() },
            exchange = instrument.exchange,
            lot = instrument.lot.toLong(),
            currency = instrument.currency.takeIf { it.isNotBlank() },
            settlementCurrency = null,
            priceStepCost = null,
            minPriceIncrement = TInvestConverters.quotationToDouble(instrument.minPriceIncrement),
            expirationDate = null,
            apiTradeAvailableFlag = instrument.apiTradeAvailableFlag,
            buyAvailableFlag = instrument.buyAvailableFlag,
            sellAvailableFlag = instrument.sellAvailableFlag,
            shortEnabledFlag = instrument.shortEnabledFlag,
            forIisFlag = instrument.forIisFlag,
            forQualInvestorFlag = instrument.forQualInvestorFlag,
            weekendFlag = instrument.weekendFlag,
            blockedTcaFlag = instrument.blockedTcaFlag,
            instrumentType = type,
        )
}
