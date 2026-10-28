package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.models.FutureUi
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

class CalculateTradeDetailsUseCaseTest {
    companion object {
        private var originalLocale: Locale? = null

        @JvmStatic
        @BeforeClass
        fun setUpLocale() {
            originalLocale = Locale.getDefault()
            // Фиксируем локаль: formatCurrency/formatPrice зависят от неё
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        }

        @JvmStatic
        @AfterClass
        fun restoreLocale() {
            originalLocale?.let { Locale.setDefault(it) }
        }
    }

    private val useCase = CalculateTradeDetailsUseCase()

    // ---------- Хелперы ----------

    private fun share(ticker: String = "SBER"): InstrumentUi =
        InstrumentUi(
            tscalpInstrumentId = "uid_$ticker",
            ticker = ticker,
            classCode = "TQBR",
            isin = "ISIN_$ticker",
            ttech_uid = "tuid_$ticker",
            name = "$ticker Inc.",
            currency = "rub",
            lot = 1,
            instrumentType = "share",
        )

    private fun future(
        ticker: String = "SBRF",
        minPriceIncrement: Double = 1.0,
        minPriceIncrementAmount: Double = 1.0,
    ): FutureUi =
        FutureUi(
            tscalpInstrumentId = "uid_$ticker",
            ticker = ticker,
            classCode = "SPBFUT",
            isin = "ISIN_$ticker",
            ttech_uid = "tuid_$ticker",
            name = "$ticker future",
            currency = "rub",
            lot = 1,
            minPriceIncrement = minPriceIncrement,
            minPriceIncrementAmount = minPriceIncrementAmount,
        )

    // ---------- executionPrice: выбор по типу ордера ----------

    @Test
    fun `market uses currentPrice`() {
        val result =
            useCase.calculate(
                currentPrice = 250.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share(),
                pairedInstrument = null,
                quantity = 1,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertEquals(250.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `limit uses limitPrice`() {
        val result =
            useCase.calculate(
                currentPrice = 250.0,
                limitPrice = "300.5",
                stopPrice = null,
                orderType = OrderTypeSelection.Limit,
                instrument = share(),
                pairedInstrument = null,
                quantity = 1,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertEquals(300.5, result.executionPrice, 0.0001)
    }

    @Test
    fun `stop-loss uses stopPrice`() {
        val result =
            useCase.calculate(
                currentPrice = 250.0,
                limitPrice = null,
                stopPrice = "240.0",
                orderType = OrderTypeSelection.StopLoss,
                instrument = share(),
                pairedInstrument = null,
                quantity = 1,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertEquals(240.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `take-profit uses stopPrice`() {
        val result =
            useCase.calculate(
                currentPrice = 250.0,
                limitPrice = null,
                stopPrice = "270.0",
                orderType = OrderTypeSelection.TakeProfit,
                instrument = share(),
                pairedInstrument = null,
                quantity = 1,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertEquals(270.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `stop-limit uses limitPrice`() {
        val result =
            useCase.calculate(
                currentPrice = 250.0,
                limitPrice = "255.0",
                stopPrice = "260.0",
                orderType = OrderTypeSelection.StopLimit,
                instrument = share(),
                pairedInstrument = null,
                quantity = 1,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertEquals(255.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `market with null currentPrice falls back to zero`() {
        val result =
            useCase.calculate(
                currentPrice = null,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share(),
                pairedInstrument = null,
                quantity = 1,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertEquals(0.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `limit with invalid limitPrice falls back to zero`() {
        val result =
            useCase.calculate(
                currentPrice = 250.0,
                limitPrice = "abc",
                stopPrice = null,
                orderType = OrderTypeSelection.Limit,
                instrument = share(),
                pairedInstrument = null,
                quantity = 1,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertEquals(0.0, result.executionPrice, 0.0001)
    }

    // ---------- costPoints / costCurrency / costDomesticCurrency ----------

    @Test
    fun `cost is null when quantity is zero`() {
        val result =
            useCase.calculate(
                currentPrice = 250.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share(),
                pairedInstrument = null,
                quantity = 0,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertNull(result.costPoints)
        assertNull(result.costCurrency)
        assertNull(result.costDomesticCurrency)
    }

    @Test
    fun `cost is null when executionPrice is zero`() {
        val result =
            useCase.calculate(
                currentPrice = null,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share(),
                pairedInstrument = null,
                quantity = 5,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertNull(result.costPoints)
        assertNull(result.costCurrency)
        assertNull(result.costDomesticCurrency)
    }

    @Test
    fun `cost for share populates costCurrency only`() {
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share(),
                pairedInstrument = null,
                quantity = 5,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertNull(result.costPoints)
        assertNotNull(result.costCurrency)
        assertEquals(500.0, result.costCurrency!!, 0.0001)
        assertNull(result.costDomesticCurrency)
    }

    @Test
    fun `cost for futures populates costPoints and costDomesticCurrency`() {
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = future(minPriceIncrement = 0.5, minPriceIncrementAmount = 10.0),
                pairedInstrument = null,
                quantity = 2,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertNull(result.costCurrency)
        assertNotNull(result.costPoints)
        assertEquals(200.0, result.costPoints!!, 0.0001)
        assertNotNull(result.costDomesticCurrency)
        assertEquals(4000.0, result.costDomesticCurrency!!, 0.0001)
    }

    @Test
    fun `cost for futures without pointValue uses multiplier 1_0`() {
        val futureWithoutPoint =
            FutureUi(
                tscalpInstrumentId = "uid_f",
                ticker = "F",
                classCode = "SPBFUT",
                isin = "ISIN_f",
                ttech_uid = "tuid_f",
                name = "F",
                currency = "rub",
                lot = 1,
                minPriceIncrement = null,
                minPriceIncrementAmount = null,
            )
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = futureWithoutPoint,
                pairedInstrument = null,
                quantity = 1,
                pairedMultiplier = null,
                pairCurrentPrice = null,
            )
        assertNull(result.costCurrency)
        assertNotNull(result.costPoints)
        assertEquals(100.0, result.costPoints!!, 0.0001)
        assertNotNull(result.costDomesticCurrency)
        assertEquals(100.0, result.costDomesticCurrency!!, 0.0001)
    }

    // ---------- multiplierPoints / multiplierCurrency / multiplierDomesticCurrency ----------

    @Test
    fun `multiplier is null when pairedInstrument is null`() {
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share(),
                pairedInstrument = null,
                quantity = 5,
                pairedMultiplier = "2.0",
                pairCurrentPrice = 50.0,
            )
        assertNull(result.multiplierPoints)
        assertNull(result.multiplierCurrency)
        assertNull(result.multiplierDomesticCurrency)
    }

    @Test
    fun `multiplier is null when pairedMultiplier is null`() {
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share(),
                pairedInstrument = share("GAZP"),
                quantity = 5,
                pairedMultiplier = null,
                pairCurrentPrice = 50.0,
            )
        assertNull(result.multiplierPoints)
        assertNull(result.multiplierCurrency)
        assertNull(result.multiplierDomesticCurrency)
    }

    @Test
    fun `multiplier for market pair uses pairCurrentPrice`() {
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share("SBER"),
                pairedInstrument = share("GAZP"),
                quantity = 5,
                pairedMultiplier = "2.0",
                pairCurrentPrice = 50.0,
            )
        assertNull(result.multiplierPoints)
        assertNotNull(result.multiplierCurrency)
        assertEquals(500.0, result.multiplierCurrency!!, 0.0001)
        assertNull(result.multiplierDomesticCurrency)
    }

    @Test
    fun `multiplier for limit pair uses limitPrice not pairCurrentPrice`() {
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = "60.0",
                stopPrice = null,
                orderType = OrderTypeSelection.Limit,
                instrument = share("SBER"),
                pairedInstrument = share("GAZP"),
                quantity = 5,
                pairedMultiplier = "2.0",
                pairCurrentPrice = 50.0,
            )
        assertNull(result.multiplierPoints)
        assertNotNull(result.multiplierCurrency)
        assertEquals(600.0, result.multiplierCurrency!!, 0.0001)
        assertNull(result.multiplierDomesticCurrency)
    }

    @Test
    fun `multiplier for futures pair populates points and domestic currency`() {
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share("SBER"),
                pairedInstrument = future(minPriceIncrement = 0.5, minPriceIncrementAmount = 10.0),
                quantity = 2,
                pairedMultiplier = "3.0",
                pairCurrentPrice = 100.0,
            )
        assertNull(result.multiplierCurrency)
        assertNotNull(result.multiplierPoints)
        assertEquals(600.0, result.multiplierPoints!!, 0.0001)
        assertNotNull(result.multiplierDomesticCurrency)
        assertEquals(12000.0, result.multiplierDomesticCurrency!!, 0.0001)
    }

    @Test
    fun `multiplier with invalid multiplier falls back to 1_0`() {
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share("SBER"),
                pairedInstrument = share("GAZP"),
                quantity = 5,
                pairedMultiplier = "abc",
                pairCurrentPrice = 50.0,
            )
        assertNull(result.multiplierPoints)
        assertNotNull(result.multiplierCurrency)
        assertEquals(250.0, result.multiplierCurrency!!, 0.0001)
        assertNull(result.multiplierDomesticCurrency)
    }

    @Test
    fun `multiplier is null when pair executionPrice is zero`() {
        val result =
            useCase.calculate(
                currentPrice = 100.0,
                limitPrice = null,
                stopPrice = null,
                orderType = OrderTypeSelection.Market,
                instrument = share("SBER"),
                pairedInstrument = share("GAZP"),
                quantity = 5,
                pairedMultiplier = "2.0",
                pairCurrentPrice = null, // для Market → 0.0
            )
        assertNull(result.multiplierPoints)
        assertNull(result.multiplierCurrency)
        assertNull(result.multiplierDomesticCurrency)
    }
}
