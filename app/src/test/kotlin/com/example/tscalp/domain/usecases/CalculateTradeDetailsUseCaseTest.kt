package com.example.tscalp.domain.usecases

import com.example.tscalp.domain.models.FutureUi
import com.example.tscalp.domain.models.InstrumentUi
import com.example.tscalp.domain.models.OrderTypeSelection
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun share(ticker: String = "SBER"): InstrumentUi = InstrumentUi(
        tscalpInstrumentId = "uid_$ticker",
        ticker = ticker,
        classCode = "TQBR",
        isin = "ISIN_$ticker",
        ttech_uid = "tuid_$ticker",
        name = "$ticker Inc.",
        currency = "rub",
        lot = 1,
        instrumentType = "share"
    )

    private fun future(
        ticker: String = "SBRF",
        minPriceIncrement: Double = 1.0,
        minPriceIncrementAmount: Double = 1.0
    ): FutureUi = FutureUi(
        tscalpInstrumentId = "uid_$ticker",
        ticker = ticker,
        classCode = "SPBFUT",
        isin = "ISIN_$ticker",
        ttech_uid = "tuid_$ticker",
        name = "$ticker future",
        currency = "rub",
        lot = 1,
        minPriceIncrement = minPriceIncrement,
        minPriceIncrementAmount = minPriceIncrementAmount
    )

    // ---------- executionPrice: выбор по типу ордера ----------

    @Test
    fun `market uses currentPrice`() {
        val result = useCase.calculate(
            currentPrice = 250.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share(),
            pairedInstrument = null,
            quantity = 1,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertEquals(250.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `limit uses limitPrice`() {
        val result = useCase.calculate(
            currentPrice = 250.0,
            limitPrice = "300.5",
            stopPrice = null,
            orderType = OrderTypeSelection.Limit,
            instrument = share(),
            pairedInstrument = null,
            quantity = 1,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertEquals(300.5, result.executionPrice, 0.0001)
    }

    @Test
    fun `stop-loss uses stopPrice`() {
        val result = useCase.calculate(
            currentPrice = 250.0,
            limitPrice = null,
            stopPrice = "240.0",
            orderType = OrderTypeSelection.StopLoss,
            instrument = share(),
            pairedInstrument = null,
            quantity = 1,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertEquals(240.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `take-profit uses stopPrice`() {
        val result = useCase.calculate(
            currentPrice = 250.0,
            limitPrice = null,
            stopPrice = "270.0",
            orderType = OrderTypeSelection.TakeProfit,
            instrument = share(),
            pairedInstrument = null,
            quantity = 1,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertEquals(270.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `stop-limit uses limitPrice`() {
        val result = useCase.calculate(
            currentPrice = 250.0,
            limitPrice = "255.0",
            stopPrice = "260.0",
            orderType = OrderTypeSelection.StopLimit,
            instrument = share(),
            pairedInstrument = null,
            quantity = 1,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertEquals(255.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `market with null currentPrice falls back to zero`() {
        val result = useCase.calculate(
            currentPrice = null,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share(),
            pairedInstrument = null,
            quantity = 1,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertEquals(0.0, result.executionPrice, 0.0001)
    }

    @Test
    fun `limit with invalid limitPrice falls back to zero`() {
        val result = useCase.calculate(
            currentPrice = 250.0,
            limitPrice = "abc",
            stopPrice = null,
            orderType = OrderTypeSelection.Limit,
            instrument = share(),
            pairedInstrument = null,
            quantity = 1,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertEquals(0.0, result.executionPrice, 0.0001)
    }

    // ---------- costOverlay ----------

    @Test
    fun `costOverlay is null when quantity is zero`() {
        val result = useCase.calculate(
            currentPrice = 250.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share(),
            pairedInstrument = null,
            quantity = 0,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertNull(result.costOverlay)
    }

    @Test
    fun `costOverlay is null when executionPrice is zero`() {
        val result = useCase.calculate(
            currentPrice = null,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share(),
            pairedInstrument = null,
            quantity = 5,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertNull(result.costOverlay)
    }

    @Test
    fun `costOverlay for share contains currency only`() {
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share(),
            pairedInstrument = null,
            quantity = 5,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        assertNotNull(result.costOverlay)
        assertTrue("Ожидалось '₽' в: ${result.costOverlay}", result.costOverlay!!.contains("₽"))
        assertTrue("Не ожидалось 'пт' для акции: ${result.costOverlay}", !result.costOverlay.contains("пт"))
    }

    @Test
    fun `costOverlay for futures contains points and currency`() {
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = future(minPriceIncrement = 0.5, minPriceIncrementAmount = 10.0),
            pairedInstrument = null,
            quantity = 2,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        val overlay = result.costOverlay!!
        assertTrue("Ожидалось 'пт' в: $overlay", overlay.contains("пт"))
        assertTrue("Ожидалось '₽' в: $overlay", overlay.contains("₽"))
        assertTrue("Ожидался разделитель '·' в: $overlay", overlay.contains("·"))
    }

    @Test
    fun `costOverlay for futures without pointValue uses multiplier 1_0`() {
        // FutureUi с minPriceIncrementAmount = null → pointValue = 1.0 по умолчанию
        val futureWithoutPoint = FutureUi(
            tscalpInstrumentId = "uid_f",
            ticker = "F",
            classCode = "SPBFUT",
            isin = "ISIN_f",
            ttech_uid = "tuid_f",
            name = "F",
            currency = "rub",
            lot = 1,
            minPriceIncrement = null,
            minPriceIncrementAmount = null
        )
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = futureWithoutPoint,
            pairedInstrument = null,
            quantity = 1,
            pairedMultiplier = null,
            pairCurrentPrice = null
        )
        val overlay = result.costOverlay!!
        assertTrue("Ожидалось 'пт' в: $overlay", overlay.contains("пт"))
        assertTrue("Ожидалось '₽' в: $overlay", overlay.contains("₽"))
    }

    // ---------- multiplierOverlay ----------

    @Test
    fun `multiplierOverlay is null when pairedInstrument is null`() {
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share(),
            pairedInstrument = null,
            quantity = 5,
            pairedMultiplier = "2.0",
            pairCurrentPrice = 50.0
        )
        assertNull(result.multiplierOverlay)
    }

    @Test
    fun `multiplierOverlay is null when pairedMultiplier is null`() {
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share(),
            pairedInstrument = share("GAZP"),
            quantity = 5,
            pairedMultiplier = null,
            pairCurrentPrice = 50.0
        )
        assertNull(result.multiplierOverlay)
    }

    @Test
    fun `multiplierOverlay for market pair uses pairCurrentPrice`() {
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share("SBER"),
            pairedInstrument = share("GAZP"),
            quantity = 5,
            pairedMultiplier = "2.0",
            pairCurrentPrice = 50.0
        )
        // 5 * 2.0 * 50 = 500 ₽
        val overlay = result.multiplierOverlay!!
        assertTrue("Ожидалось '₽' в: $overlay", overlay.contains("₽"))
        // Проверим, что использована цена 50, а не 100: результат должен быть про 500
        assertTrue("Ожидалось '500' в: $overlay", overlay.contains("500"))
    }

    @Test
    fun `multiplierOverlay for limit pair uses limitPrice not pairCurrentPrice`() {
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = "60.0",
            stopPrice = null,
            orderType = OrderTypeSelection.Limit,
            instrument = share("SBER"),
            pairedInstrument = share("GAZP"),
            quantity = 5,
            pairedMultiplier = "2.0",
            pairCurrentPrice = 50.0
        )
        // 5 * 2.0 * 60 = 600 ₽ (а не 500)
        val overlay = result.multiplierOverlay!!
        assertTrue("Ожидалось '600' в: $overlay", overlay.contains("600"))
    }

    @Test
    fun `multiplierOverlay for futures pair contains points and currency`() {
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share("SBER"),
            pairedInstrument = future(minPriceIncrement = 0.5, minPriceIncrementAmount = 10.0),
            quantity = 2,
            pairedMultiplier = "3.0",
            pairCurrentPrice = 100.0
        )
        val overlay = result.multiplierOverlay!!
        assertTrue("Ожидалось 'пт' в: $overlay", overlay.contains("пт"))
        assertTrue("Ожидалось '₽' в: $overlay", overlay.contains("₽"))
    }

    @Test
    fun `multiplierOverlay with invalid multiplier falls back to 1_0`() {
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share("SBER"),
            pairedInstrument = share("GAZP"),
            quantity = 5,
            pairedMultiplier = "abc",
            pairCurrentPrice = 50.0
        )
        // 5 * 1.0 * 50 = 250 ₽
        val overlay = result.multiplierOverlay!!
        assertTrue("Ожидалось '250' в: $overlay", overlay.contains("250"))
    }

    @Test
    fun `multiplierOverlay is null when pair executionPrice is zero`() {
        val result = useCase.calculate(
            currentPrice = 100.0,
            limitPrice = null,
            stopPrice = null,
            orderType = OrderTypeSelection.Market,
            instrument = share("SBER"),
            pairedInstrument = share("GAZP"),
            quantity = 5,
            pairedMultiplier = "2.0",
            pairCurrentPrice = null // для Market → 0.0
        )
        assertNull(result.multiplierOverlay)
    }
}
