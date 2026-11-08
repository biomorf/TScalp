package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.Bond
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Currency
import com.gitlab.biomorf.tscalp.domain.models.Etf
import com.gitlab.biomorf.tscalp.domain.models.Future
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.Option
import com.gitlab.biomorf.tscalp.domain.models.Other
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.transport.ktor.RestInstrument

// ---------- RestInstrument → 7 sealed subtypes ----------
// Dispatcher (`toInstrument`) — в TInvestRestMappers.kt.
// Subtype-specific поля (margin, share-/bond-/option-only) не приходят
// в REST GetInstrumentBy; null по §5 `docs/domain_models.md`.

internal fun RestInstrument.toFuture(): Future =
    Future(
        brokerName = BrokerName.TINVEST,
        tscalpInstrumentId = uid,
        ticker = ticker,
        name = name,
        isin = isin ?: "",
        classCode = classCode.takeIf { it.isNotBlank() },
        figi = figi?.takeIf { it.isNotBlank() },
        exchange = exchange,
        lot = lot.toLong(),
        currency = currency.takeIf { it.isNotBlank() },
        settlementCurrency = null,
        priceStepCost = null,
        minPriceIncrement = minPriceIncrement.toDouble(),
        expirationDate = null,
        apiTradeAvailableFlag = apiTradeAvailableFlag,
        buyAvailableFlag = buyAvailableFlag,
        sellAvailableFlag = sellAvailableFlag,
        shortEnabledFlag = shortEnabledFlag,
        forIisFlag = forIisFlag,
        forQualInvestorFlag = forQualInvestorFlag,
        weekendFlag = weekendFlag,
        blockedTcaFlag = blockedTcaFlag,
        instrumentType = InstrumentType.FUTURES,
    )

internal fun RestInstrument.toShare(): Share =
    Share(
        brokerName = BrokerName.TINVEST,
        tscalpInstrumentId = uid,
        ticker = ticker,
        name = name,
        isin = isin ?: "",
        classCode = classCode.takeIf { it.isNotBlank() },
        figi = figi?.takeIf { it.isNotBlank() },
        exchange = exchange,
        lot = lot.toLong(),
        currency = currency.takeIf { it.isNotBlank() },
        settlementCurrency = null,
        priceStepCost = null,
        minPriceIncrement = minPriceIncrement.toDouble(),
        expirationDate = null,
        apiTradeAvailableFlag = apiTradeAvailableFlag,
        buyAvailableFlag = buyAvailableFlag,
        sellAvailableFlag = sellAvailableFlag,
        shortEnabledFlag = shortEnabledFlag,
        forIisFlag = forIisFlag,
        forQualInvestorFlag = forQualInvestorFlag,
        weekendFlag = weekendFlag,
        blockedTcaFlag = blockedTcaFlag,
        instrumentType = InstrumentType.SHARE,
    )

internal fun RestInstrument.toBond(): Bond =
    Bond(
        brokerName = BrokerName.TINVEST,
        tscalpInstrumentId = uid,
        ticker = ticker,
        name = name,
        isin = isin ?: "",
        classCode = classCode.takeIf { it.isNotBlank() },
        figi = figi?.takeIf { it.isNotBlank() },
        exchange = exchange,
        lot = lot.toLong(),
        currency = currency.takeIf { it.isNotBlank() },
        settlementCurrency = null,
        priceStepCost = null,
        minPriceIncrement = minPriceIncrement.toDouble(),
        expirationDate = null,
        apiTradeAvailableFlag = apiTradeAvailableFlag,
        buyAvailableFlag = buyAvailableFlag,
        sellAvailableFlag = sellAvailableFlag,
        shortEnabledFlag = shortEnabledFlag,
        forIisFlag = forIisFlag,
        forQualInvestorFlag = forQualInvestorFlag,
        weekendFlag = weekendFlag,
        blockedTcaFlag = blockedTcaFlag,
        instrumentType = InstrumentType.BOND,
    )

internal fun RestInstrument.toOption(): Option =
    Option(
        brokerName = BrokerName.TINVEST,
        tscalpInstrumentId = uid,
        ticker = ticker,
        name = name,
        isin = isin ?: "",
        classCode = classCode.takeIf { it.isNotBlank() },
        figi = figi?.takeIf { it.isNotBlank() },
        exchange = exchange,
        lot = lot.toLong(),
        currency = currency.takeIf { it.isNotBlank() },
        settlementCurrency = null,
        priceStepCost = null,
        minPriceIncrement = minPriceIncrement.toDouble(),
        expirationDate = null,
        apiTradeAvailableFlag = apiTradeAvailableFlag,
        buyAvailableFlag = buyAvailableFlag,
        sellAvailableFlag = sellAvailableFlag,
        shortEnabledFlag = shortEnabledFlag,
        forIisFlag = forIisFlag,
        forQualInvestorFlag = forQualInvestorFlag,
        weekendFlag = weekendFlag,
        blockedTcaFlag = blockedTcaFlag,
        instrumentType = InstrumentType.OPTION,
    )

internal fun RestInstrument.toEtf(): Etf =
    Etf(
        brokerName = BrokerName.TINVEST,
        tscalpInstrumentId = uid,
        ticker = ticker,
        name = name,
        isin = isin ?: "",
        classCode = classCode.takeIf { it.isNotBlank() },
        figi = figi?.takeIf { it.isNotBlank() },
        exchange = exchange,
        lot = lot.toLong(),
        currency = currency.takeIf { it.isNotBlank() },
        settlementCurrency = null,
        priceStepCost = null,
        minPriceIncrement = minPriceIncrement.toDouble(),
        expirationDate = null,
        apiTradeAvailableFlag = apiTradeAvailableFlag,
        buyAvailableFlag = buyAvailableFlag,
        sellAvailableFlag = sellAvailableFlag,
        shortEnabledFlag = shortEnabledFlag,
        forIisFlag = forIisFlag,
        forQualInvestorFlag = forQualInvestorFlag,
        weekendFlag = weekendFlag,
        blockedTcaFlag = blockedTcaFlag,
        instrumentType = InstrumentType.ETF,
    )

internal fun RestInstrument.toCurrency(): Currency =
    Currency(
        brokerName = BrokerName.TINVEST,
        tscalpInstrumentId = uid,
        ticker = ticker,
        name = name,
        isin = isin ?: "",
        classCode = classCode.takeIf { it.isNotBlank() },
        figi = figi?.takeIf { it.isNotBlank() },
        exchange = exchange,
        lot = lot.toLong(),
        currency = currency.takeIf { it.isNotBlank() },
        settlementCurrency = null,
        priceStepCost = null,
        minPriceIncrement = minPriceIncrement.toDouble(),
        expirationDate = null,
        apiTradeAvailableFlag = apiTradeAvailableFlag,
        buyAvailableFlag = buyAvailableFlag,
        sellAvailableFlag = sellAvailableFlag,
        shortEnabledFlag = shortEnabledFlag,
        forIisFlag = forIisFlag,
        forQualInvestorFlag = forQualInvestorFlag,
        weekendFlag = weekendFlag,
        blockedTcaFlag = blockedTcaFlag,
        instrumentType = InstrumentType.CURRENCY,
    )

internal fun RestInstrument.toOther(): Other =
    Other(
        brokerName = BrokerName.TINVEST,
        tscalpInstrumentId = uid,
        ticker = ticker,
        name = name,
        isin = isin ?: "",
        classCode = classCode.takeIf { it.isNotBlank() },
        figi = figi?.takeIf { it.isNotBlank() },
        exchange = exchange,
        lot = lot.toLong(),
        currency = currency.takeIf { it.isNotBlank() },
        settlementCurrency = null,
        priceStepCost = null,
        minPriceIncrement = minPriceIncrement.toDouble(),
        expirationDate = null,
        apiTradeAvailableFlag = apiTradeAvailableFlag,
        buyAvailableFlag = buyAvailableFlag,
        sellAvailableFlag = sellAvailableFlag,
        shortEnabledFlag = shortEnabledFlag,
        forIisFlag = forIisFlag,
        forQualInvestorFlag = forQualInvestorFlag,
        weekendFlag = weekendFlag,
        blockedTcaFlag = blockedTcaFlag,
        instrumentType = InstrumentType.fromBrokerString(instrumentType),
    )
