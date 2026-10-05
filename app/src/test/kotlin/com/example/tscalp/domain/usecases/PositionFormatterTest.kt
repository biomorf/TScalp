package com.example.tscalp.util

import androidx.compose.ui.graphics.Color
import com.example.tscalp.domain.models.PortfolioPosition
import com.example.tscalp.domain.models.BrokerName
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

class PositionFormatterTest {

    companion object {
        private var originalLocale: Locale? = null

        @JvmStatic
        @BeforeClass
        fun setUpLocale() {
            originalLocale = Locale.getDefault()
            // formatCurrency зависит от локали — фиксируем ru-RU
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        }

        @JvmStatic
        @AfterClass
        fun restoreLocale() {
            originalLocale?.let { Locale.setDefault(it) }
        }
    }

    // ---------- Хелперы ----------

    private fun position(
        ticker: String = "SBER",
        quantity: Long = 10L,
        currentPrice: Double = 250.0,
        averagePrice: Double? = 240.0,
        totalValue: Double = currentPrice * quantity,
        profit: Double? = null,
        profitPercent: Double? = null
    ): PortfolioPosition = PortfolioPosition(
        tscalpInstrumentId = "uid_$ticker",
        brokerName = BrokerName.TINVEST,
        ticker = ticker,
        name = "$ticker Inc.",
        quantity = quantity,
        currentPrice = currentPrice,
        averagePrice = averagePrice,
        totalValue = totalValue,
        profit = profit,
        profitPercent = profitPercent
    )

    // ---------- formatAverage ----------

    @Test
    fun `formatAverage returns null pair when averagePrice is null`() {
        val (total, detail) = PositionFormatter.formatAverage(
            position(averagePrice = null),
            instrumentType = "share"
        )
        assertNull(total)
        assertNull(detail)
    }

    @Test
    fun `formatAverage returns null pair when averagePrice is zero`() {
        val (total, detail) = PositionFormatter.formatAverage(
            position(averagePrice = 0.0),
            instrumentType = "share"
        )
        assertNull(total)
        assertNull(detail)
    }

    @Test
    fun `formatAverage returns null pair when averagePrice is negative`() {
        val (total, detail) = PositionFormatter.formatAverage(
            position(averagePrice = -1.0),
            instrumentType = "share"
        )
        assertNull(total)
        assertNull(detail)
    }

    @Test
    fun `formatAverage for share contains currency and quantity`() {
        val (total, detail) = PositionFormatter.formatAverage(
            position(quantity = 5L, averagePrice = 100.0),
            instrumentType = "share"
        )
        assertNotNull(total)
        assertNotNull(detail)
        assertTrue("Ожидалось '₽' в total: $total", total!!.contains("₽"))
        assertTrue("Ожидалось '5 лот' в detail: $detail", detail!!.contains("5 лот"))
        assertTrue("Ожидалось '₽' в detail: $detail", detail.contains("₽"))
    }

    @Test
    fun `formatAverage for futures contains points and currency`() {
        val (total, detail) = PositionFormatter.formatAverage(
            position(quantity = 2L, averagePrice = 100.0),
            instrumentType = "futures"
        )
        assertNotNull(total)
        assertNotNull(detail)
        assertTrue("Ожидалось 'пт' в total: $total", total!!.contains("пт"))
        assertTrue("Ожидалось 'пт' в detail: $detail", detail!!.contains("пт"))
    }

    // ---------- formatProfit: null profit ----------

    @Test
    fun `formatProfit returns null string when profit is null`() {
        val (profitStr, _, percentStr) = PositionFormatter.formatProfit(
            position(profit = null, profitPercent = null),
            instrumentType = "share",
            pointValue = null
        )
        assertNull(profitStr)
        assertNull(percentStr)
    }

    // ---------- formatProfit: share ----------

    @Test
    fun `formatProfit for positive share contains plus and currency`() {
        val (profitStr, color, _) = PositionFormatter.formatProfit(
            position(profit = 100.0),
            instrumentType = "share",
            pointValue = null
        )
        assertNotNull(profitStr)
        assertTrue("Ожидался '+' в: $profitStr", profitStr!!.startsWith("+"))
        assertTrue("Ожидалось '₽' в: $profitStr", profitStr.contains("₽"))
        assertEquals(Color(0xFF2E7D32), color)
    }

    @Test
    fun `formatProfit for negative share contains minus and currency`() {
        val (profitStr, color, _) = PositionFormatter.formatProfit(
            position(profit = -50.0),
            instrumentType = "share",
            pointValue = null
        )
        assertNotNull(profitStr)
        assertTrue("Ожидался '-' в: $profitStr", profitStr!!.startsWith("-"))
        assertTrue("Ожидалось '₽' в: $profitStr", profitStr.contains("₽"))
        assertEquals(Color(0xFFC62828), color)
    }

    @Test
    fun `formatProfit for zero share produces plus sign`() {
        val (profitStr, color, _) = PositionFormatter.formatProfit(
            position(profit = 0.0),
            instrumentType = "share",
            pointValue = null
        )
        assertNotNull(profitStr)
        // profit >= 0 → знак "+"
        assertTrue("Ожидался '+' в: $profitStr", profitStr!!.startsWith("+"))
        assertEquals(Color(0xFF2E7D32), color)
    }

    // ---------- formatProfit: futures ----------

    @Test
    fun `formatProfit for futures with pointValue contains points and currency`() {
        val (profitStr, color, _) = PositionFormatter.formatProfit(
            position(profit = 10.0),
            instrumentType = "futures",
            pointValue = 5.0
        )
        assertNotNull(profitStr)
        assertTrue("Ожидалось 'пт' в: $profitStr", profitStr!!.contains("пт"))
        assertTrue("Ожидалось '₽' в: $profitStr", profitStr.contains("₽"))
        assertTrue("Ожидался разделитель '·' в: $profitStr", profitStr.contains("·"))
        assertTrue("Ожидался '+' в: $profitStr", profitStr.startsWith("+"))
        assertEquals(Color(0xFF2E7D32), color)
    }

    @Test
    fun `formatProfit for futures without pointValue contains only points`() {
        val (profitStr, _, _) = PositionFormatter.formatProfit(
            position(profit = 10.0),
            instrumentType = "futures",
            pointValue = null
        )
        assertNotNull(profitStr)
        assertTrue("Ожидалось 'пт' в: $profitStr", profitStr!!.contains("пт"))
        assertTrue("Не ожидалось '₽' в: $profitStr", !profitStr.contains("₽"))
        assertTrue("Не ожидалось '·' в: $profitStr", !profitStr.contains("·"))
    }

    @Test
    fun `formatProfit for futures with zero pointValue contains only points`() {
        val (profitStr, _, _) = PositionFormatter.formatProfit(
            position(profit = 10.0),
            instrumentType = "futures",
            pointValue = 0.0
        )
        assertNotNull(profitStr)
        assertTrue("Ожидалось 'пт' в: $profitStr", profitStr!!.contains("пт"))
        assertTrue("Не ожидалось '₽' в: $profitStr", !profitStr.contains("₽"))
    }

    @Test
    fun `formatProfit for negative futures contains minus sign`() {
        val (profitStr, color, _) = PositionFormatter.formatProfit(
            position(profit = -25.5),
            instrumentType = "futures",
            pointValue = 5.0
        )
        assertNotNull(profitStr)
        // pointsStr = "-25.50", знак не добавляем (минус уже есть)
        assertTrue("Ожидался '-' в: $profitStr", profitStr!!.startsWith("-"))
        assertEquals(Color(0xFFC62828), color)
    }

    // ---------- formatProfit: percentage ----------

    @Test
    fun `formatProfit percentage for positive uses plus`() {
        val (_, _, percentStr) = PositionFormatter.formatProfit(
            position(profit = 10.0, profitPercent = 5.25),
            instrumentType = "share",
            pointValue = null
        )
        assertNotNull(percentStr)
        assertTrue("Ожидался '+' в: $percentStr", percentStr!!.startsWith("+"))
        assertTrue("Ожидалось '%' в: $percentStr", percentStr.endsWith("%"))
    }

    @Test
    fun `formatProfit percentage for negative uses minus`() {
        val (_, _, percentStr) = PositionFormatter.formatProfit(
            position(profit = -10.0, profitPercent = -3.5),
            instrumentType = "share",
            pointValue = null
        )
        assertNotNull(percentStr)
        assertTrue("Ожидался '-' в: $percentStr", percentStr!!.startsWith("-"))
    }

    @Test
    fun `formatProfit percentage is null when profitPercent is null`() {
        val (_, _, percentStr) = PositionFormatter.formatProfit(
            position(profit = 10.0, profitPercent = null),
            instrumentType = "share",
            pointValue = null
        )
        assertNull(percentStr)
    }

    // ---------- formatCurrentPrice ----------

    @Test
    fun `formatCurrentPrice returns null pair when price is zero`() {
        val (price, rub) = PositionFormatter.formatCurrentPrice(
            position(currentPrice = 0.0),
            instrumentType = "share",
            pointValue = null
        )
        assertNull(price)
        assertNull(rub)
    }

    @Test
    fun `formatCurrentPrice for share has no rub extra`() {
        val (price, rub) = PositionFormatter.formatCurrentPrice(
            position(currentPrice = 250.0),
            instrumentType = "share",
            pointValue = null
        )
        assertNotNull(price)
        assertNull(rub)
        assertTrue("Ожидалось '₽' в: $price", price!!.contains("₽"))
    }

    @Test
    fun `formatCurrentPrice for futures contains points and rub extra`() {
        val (price, rub) = PositionFormatter.formatCurrentPrice(
            position(currentPrice = 100.0),
            instrumentType = "futures",
            pointValue = 5.0
        )
        assertNotNull(price)
        assertNotNull(rub)
        assertTrue("Ожидалось 'пт' в price: $price", price!!.contains("пт"))
        assertTrue("Ожидалось '₽' в rub: $rub", rub!!.contains("₽"))
    }

    @Test
    fun `formatCurrentPrice for futures without pointValue has no rub extra`() {
        val (price, rub) = PositionFormatter.formatCurrentPrice(
            position(currentPrice = 100.0),
            instrumentType = "futures",
            pointValue = null
        )
        assertNotNull(price)
        assertNull(rub)
    }

    // ---------- priceChangeColor ----------

    @Test
    fun `priceChangeColor returns green for positive`() {
        assertEquals(Color(0xFF2E7D32), PositionFormatter.priceChangeColor(5.0))
    }

    @Test
    fun `priceChangeColor returns green for zero`() {
        assertEquals(Color(0xFF2E7D32), PositionFormatter.priceChangeColor(0.0))
    }

    @Test
    fun `priceChangeColor returns red for negative`() {
        assertEquals(Color(0xFFC62828), PositionFormatter.priceChangeColor(-3.0))
    }

    @Test
    fun `priceChangeColor returns unspecified for null`() {
        assertEquals(Color.Unspecified, PositionFormatter.priceChangeColor(null))
    }
}
