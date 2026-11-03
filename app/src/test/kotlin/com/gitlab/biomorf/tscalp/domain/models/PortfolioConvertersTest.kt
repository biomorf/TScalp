package com.gitlab.biomorf.tscalp.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты конвертеров [PositionStreamItem.toPosition] / [PositionStreamItem.toAccountBalance].
 * 7 subtypes Position + edge cases (null instrument, null accountId, domestic
 * currency, mismatch, OTHER fallback, balance-only route).
 */
class PortfolioConvertersTest {
    private val share =
        Share(
            tscalpInstrumentId = "uid-share",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )
    private val future =
        Future(
            tscalpInstrumentId = "uid-future",
            ticker = "SiU4",
            name = "Si",
            isin = "FUT-SI-2024",
        )
    private val bond =
        Bond(
            tscalpInstrumentId = "uid-bond",
            ticker = "OFZ26207",
            name = "ОФЗ 26207",
            isin = "RU000A0JS5T0",
        )
    private val option =
        Option(
            tscalpInstrumentId = "uid-option",
            ticker = "SiU4-C-85000",
            name = "Call Si",
            isin = "OPT-SI-2024",
        )
    private val etf =
        Etf(
            tscalpInstrumentId = "uid-etf",
            ticker = "TMOS",
            name = "Тинькофф iMOEX",
            isin = "RU000A1036D9",
        )
    private val currency =
        Currency(
            tscalpInstrumentId = "uid-usd",
            ticker = "USD000UTSTOM",
            name = "Доллар США",
            isin = "USD-CURRENCY",
            currency = "usd",
        )
    private val other =
        Other(
            tscalpInstrumentId = "uid-other",
            ticker = "OTHER",
            name = "Прочее",
            isin = "OTHER-1",
        )

    private fun item(
        instrumentType: InstrumentType = InstrumentType.SHARE,
        accountId: String? = "acc-1",
        isDomesticCurrency: Boolean = false,
        currency: String? = null,
        money: Double? = null,
        blocked: Double? = null,
        tscalpInstrumentId: String = "uid-share",
        ticker: String = "SBER",
    ): PositionStreamItem =
        PositionStreamItem(
            tscalpInstrumentId = tscalpInstrumentId,
            brokerName = BrokerName.TINVEST,
            ticker = ticker,
            quantity = 10L,
            currentPrice = 250.0,
            averagePositionPrice = 200.0,
            expectedYield = 500.0,
            instrumentType = instrumentType,
            accountId = accountId,
            isDomesticCurrency = isDomesticCurrency,
            currency = currency,
            money = money,
            blocked = blocked,
        )

    // ----- toPosition: 7 subtypes -----

    @Test
    fun `share produces SharePosition`() {
        val pos = item().toPosition(share)
        assertTrue(pos is SharePosition)
        assertEquals("acc-1", pos!!.accountId)
        assertEquals(10.0, pos.quantity, 0.0)
        assertEquals(250.0, pos.currentPrice, 0.0)
        assertEquals(200.0, pos.averagePrice!!, 0.0)
        assertEquals(500.0, pos.profit!!, 0.0)
    }

    @Test
    fun `future produces FuturePosition`() {
        val pos = item(instrumentType = InstrumentType.FUTURES).toPosition(future)
        assertTrue(pos is FuturePosition)
    }

    @Test
    fun `bond produces BondPosition`() {
        val pos = item(instrumentType = InstrumentType.BOND).toPosition(bond)
        assertTrue(pos is BondPosition)
    }

    @Test
    fun `option produces OptionPosition`() {
        val pos = item(instrumentType = InstrumentType.OPTION).toPosition(option)
        assertTrue(pos is OptionPosition)
    }

    @Test
    fun `etf produces EtfPosition`() {
        val pos = item(instrumentType = InstrumentType.ETF).toPosition(etf)
        assertTrue(pos is EtfPosition)
    }

    @Test
    fun `foreign currency produces CurrencyPosition`() {
        val pos =
            item(
                instrumentType = InstrumentType.CURRENCY,
                isDomesticCurrency = false,
                currency = "usd",
            ).toPosition(currency)
        assertTrue(pos is CurrencyPosition)
    }

    @Test
    fun `other produces OtherPosition`() {
        val pos = item(instrumentType = InstrumentType.OTHER).toPosition(other)
        assertTrue(pos is OtherPosition)
    }

    // ----- toPosition: edge cases -----

    @Test
    fun `null instrument returns null`() {
        assertNull(item().toPosition(null))
    }

    @Test
    fun `null accountId returns null`() {
        assertNull(item(accountId = null).toPosition(share))
    }

    @Test
    fun `domestic currency returns null (AccountBalance route)`() {
        val pos =
            item(
                instrumentType = InstrumentType.CURRENCY,
                isDomesticCurrency = true,
                currency = "rub",
            ).toPosition(currency)
        assertNull(pos)
    }

    @Test
    fun `mismatch instrumentType returns null`() {
        // item says SHARE, instrument is Future
        val pos = item(instrumentType = InstrumentType.SHARE).toPosition(future)
        assertNull(pos)
    }

    @Test
    fun `OTHER item type trusts instrument`() {
        val pos = item(instrumentType = InstrumentType.OTHER).toPosition(share)
        assertTrue(pos is SharePosition)
    }

    // ----- toAccountBalance -----

    @Test
    fun `toAccountBalance domestic currency`() {
        val bal =
            item(
                instrumentType = InstrumentType.CURRENCY,
                isDomesticCurrency = true,
                currency = "rub",
                money = 1000.0,
                blocked = 500.0,
            ).toAccountBalance()
        assertEquals("rub", bal!!.currency)
        assertEquals(1000.0, bal.money, 0.0)
        assertEquals(500.0, bal.blocked, 0.0)
        assertEquals(1500.0, bal.total, 0.0)
    }

    @Test
    fun `toAccountBalance domestic currency null currency falls back to broker domestic`() {
        val bal =
            item(
                instrumentType = InstrumentType.CURRENCY,
                isDomesticCurrency = true,
                currency = null,
                money = 100.0,
                blocked = 0.0,
            ).toAccountBalance()
        assertEquals(BrokerName.TINVEST.domesticCurrency, bal!!.currency)
    }

    @Test
    fun `toAccountBalance foreign currency returns null`() {
        assertNull(
            item(
                instrumentType = InstrumentType.CURRENCY,
                isDomesticCurrency = false,
                currency = "usd",
            ).toAccountBalance(),
        )
    }

    @Test
    fun `toAccountBalance non-currency returns null`() {
        assertNull(item(instrumentType = InstrumentType.SHARE).toAccountBalance())
    }

    // ----- emptyPosition: 7 subtypes -----

    @Test
    fun `emptyPosition share produces SharePosition with zero quantity and null optional`() {
        val pos = emptyPosition(share, currentPrice = 250.0, accountId = "acc-1", brokerName = BrokerName.TINVEST)
        assertTrue(pos is SharePosition)
        assertEquals(0.0, pos.quantity, 0.0)
        assertEquals(250.0, pos.currentPrice, 0.0)
        assertNull(pos.averagePrice)
        assertNull(pos.profit)
        assertNull(pos.priceChangePercent)
        assertEquals("acc-1", pos.accountId)
        assertEquals(BrokerName.TINVEST, pos.brokerName)
    }

    @Test
    fun `emptyPosition future produces FuturePosition with lockedQuantity forwarded`() {
        val pos = emptyPosition(future, currentPrice = 85000.0, accountId = "acc-2", brokerName = BrokerName.BCS, lockedQuantity = 5.0)
        assertTrue(pos is FuturePosition)
        assertEquals(0.0, pos.quantity, 0.0)
        assertEquals(85000.0, pos.currentPrice, 0.0)
        assertEquals(5.0, pos.lockedQuantity!!, 0.0)
        assertEquals("acc-2", pos.accountId)
        assertEquals(BrokerName.BCS, pos.brokerName)
    }

    @Test
    fun `emptyPosition bond produces BondPosition`() {
        val pos = emptyPosition(bond, currentPrice = 100.0, accountId = "acc-3", brokerName = BrokerName.FINAM)
        assertTrue(pos is BondPosition)
        assertEquals(0.0, pos.quantity, 0.0)
        assertEquals("acc-3", pos.accountId)
        assertEquals(BrokerName.FINAM, pos.brokerName)
    }

    @Test
    fun `emptyPosition option produces OptionPosition`() {
        val pos = emptyPosition(option, currentPrice = 50.0, accountId = "acc-4", brokerName = BrokerName.TINVEST)
        assertTrue(pos is OptionPosition)
        assertEquals(0.0, pos.quantity, 0.0)
    }

    @Test
    fun `emptyPosition etf produces EtfPosition`() {
        val pos = emptyPosition(etf, currentPrice = 12.5, accountId = "acc-5", brokerName = BrokerName.TINVEST)
        assertTrue(pos is EtfPosition)
        assertEquals(0.0, pos.quantity, 0.0)
        assertNull(pos.lockedQuantity)
    }

    @Test
    fun `emptyPosition currency produces CurrencyPosition`() {
        val pos = emptyPosition(currency, currentPrice = 95.0, accountId = "acc-6", brokerName = BrokerName.TINVEST)
        assertTrue(pos is CurrencyPosition)
        assertEquals(0.0, pos.quantity, 0.0)
    }

    @Test
    fun `emptyPosition other produces OtherPosition`() {
        val pos = emptyPosition(other, currentPrice = 1.0, accountId = "acc-7", brokerName = BrokerName.TINVEST)
        assertTrue(pos is OtherPosition)
        assertEquals(0.0, pos.quantity, 0.0)
        assertEquals("acc-7", pos.accountId)
    }
}
