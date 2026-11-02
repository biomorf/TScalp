package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.models.Bond
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Currency
import com.gitlab.biomorf.tscalp.domain.models.Etf
import com.gitlab.biomorf.tscalp.domain.models.Future
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.Option
import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.Other
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.toOrderTypeSelection
import com.gitlab.biomorf.tscalp.transport.ktor.RestInstrument
import com.gitlab.biomorf.tscalp.transport.ktor.RestOrder
import com.gitlab.biomorf.tscalp.transport.ktor.RestPosition
import com.gitlab.biomorf.tscalp.transport.ktor.RestQuotation
import com.gitlab.biomorf.tscalp.transport.ktor.RestStopOrder

// ---------- Мапперы REST DTO → Domain Models ----------
// Используются только при fallback через REST.

internal fun RestQuotation?.toDouble(): Double? = this?.let { (units.toLongOrNull() ?: 0L) + nano / 1_000_000_000.0 }

internal fun RestQuotation?.toLong(): Long = this?.units?.toLongOrNull() ?: 0L

internal fun RestOrder.toOrder(): Order.Regular =
    Order.Regular(
        orderId = orderId,
        ticker = ticker,
        tscalpInstrumentId = instrumentUid,
        instrumentType = InstrumentType.OTHER,
        direction = direction.removePrefix("ORDER_DIRECTION_"),
        quantity = lotsRequested,
        orderType = orderType.removePrefix("ORDER_TYPE_").toOrderTypeSelection(),
        status = executionReportStatus.removePrefix("EXECUTION_REPORT_STATUS_"),
        orderDate = null,
        price = initialOrderPrice.toDouble() ?: 0.0,
        executedPrice = executedOrderPrice.toDouble(),
        executedQuantity = lotsExecuted,
    )

internal fun RestStopOrder.toOrder(): Order.Stop =
    Order.Stop(
        orderId = stopOrderId,
        ticker = ticker,
        tscalpInstrumentId = instrumentUid,
        instrumentType = InstrumentType.OTHER,
        direction = direction.removePrefix("ORDER_DIRECTION_"),
        quantity = lotsRequested,
        orderType = orderType.removePrefix("ORDER_TYPE_").toOrderTypeSelection(),
        status = executionReportStatus.removePrefix("EXECUTION_REPORT_STATUS_"),
        orderDate = null,
        price = limitPrice.toDouble() ?: 0.0,
        stopPrice = stopPrice.toDouble() ?: 0.0,
    )

internal fun RestPosition.toStreamItem(): PositionStreamItem =
    PositionStreamItem(
        tscalpInstrumentId = instrumentUid,
        brokerName = BrokerName.TINVEST,
        ticker = "",
        quantity = quantity.toLong(),
        currentPrice = currentPrice.toDouble() ?: 0.0,
        averagePositionPrice = averagePositionPrice.toDouble(),
        expectedYield = expectedYield.toDouble(),
        classCode = "",
        isin = "",
        moneyPerPointOfPrice = null,
        instrumentType = InstrumentType.fromBrokerString(instrumentType),
        currency = null,
    )

// ---------- RestInstrument → Instrument (REST fallback) ----------
// 6 mappers + dispatcher (by InstrumentType), симметрично TInvestInstrumentMapper.
// Поля, которых нет в RestInstrument, — default/null. Subtype-specific
// (margin, share-, bond-, option-only) не приходят в REST GetInstrumentBy.

internal fun RestInstrument.toInstrument(): Instrument =
    when (InstrumentType.fromBrokerString(instrumentType)) {
        InstrumentType.FUTURES -> toFuture()
        InstrumentType.SHARE -> toShare()
        InstrumentType.BOND, InstrumentType.NOTE, InstrumentType.EURO_BOND -> toBond()
        InstrumentType.OPTION -> toOption()
        InstrumentType.ETF, InstrumentType.MUTUAL_FUND -> toEtf()
        InstrumentType.CURRENCY -> toCurrency()
        else -> toOther()
    }

private fun RestInstrument.toFuture(): Future =
    Future(
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

private fun RestInstrument.toShare(): Share =
    Share(
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

private fun RestInstrument.toBond(): Bond =
    Bond(
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

private fun RestInstrument.toOption(): Option =
    Option(
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

private fun RestInstrument.toEtf(): Etf =
    Etf(
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

private fun RestInstrument.toCurrency(): Currency =
    Currency(
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

private fun RestInstrument.toOther(): Other =
    Other(
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