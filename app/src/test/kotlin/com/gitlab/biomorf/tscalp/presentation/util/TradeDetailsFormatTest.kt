package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.usecases.PairedOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.PrimaryOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.TradeDetails
import com.gitlab.biomorf.tscalp.presentation.screens.orders.OrderFormState
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.util.Locale

class TradeDetailsFormatTest {
    companion object {
        private var originalLocale: Locale? = null

        @JvmStatic
        @BeforeClass
        fun setUpLocale() {
            originalLocale = Locale.getDefault()
            Locale.setDefault(Locale.forLanguageTag("ru-RU"))
        }

        @JvmStatic
        @AfterClass
        fun restoreLocale() {
            originalLocale?.let { Locale.setDefault(it) }
        }
    }

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )
    private val gazp =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-gazp",
            ticker = "GAZP",
            name = "Газпром",
            isin = "RU0007661625",
        )

    // ---------- formatPostOrderSuccess ----------

    @Test
    fun `formatPostOrderSuccess primary regular no pair`() {
        val primary = PrimaryOutcome.Regular(orderId = "abc-1", executedLots = 3, totalLots = 5)
        val out = formatPostOrderSuccess(primary, paired = null)
        assertTrue(out, out.contains("Заявка выполнена"))
        assertTrue(out, out.contains("abc-1"))
        assertTrue(out, out.contains("Исполнено: 3/5 лотов"))
    }

    @Test
    fun `formatPostOrderSuccess primary stop uses 8-char prefix`() {
        val primary = PrimaryOutcome.Stop(orderId = "abcdef1234567890")
        val out = formatPostOrderSuccess(primary, paired = null)
        assertTrue(out, out.contains("abcdef12…"))
        assertTrue(out, out.contains("выставлена"))
    }

    @Test
    fun `formatPostOrderSuccess primary regular plus paired regular`() {
        val primary = PrimaryOutcome.Regular(orderId = "abc-1", executedLots = 1, totalLots = 1)
        val paired = PairedOutcome.Regular(instrument = gazp, orderId = "pair-1", quantity = 2)
        val out = formatPostOrderSuccess(primary, paired)
        assertTrue(out, out.contains("Контрсделка"))
        assertTrue(out, out.contains("GAZP"))
        assertTrue(out, out.contains("2 лотов"))
        assertTrue(out, out.contains("pair-1"))
    }

    @Test
    fun `formatPostOrderSuccess paired failed`() {
        val primary = PrimaryOutcome.Regular(orderId = "abc-1", executedLots = 1, totalLots = 1)
        val paired = PairedOutcome.Failed(message = "борker down", cause = null)
        val out = formatPostOrderSuccess(primary, paired)
        assertTrue(out, out.contains("Ошибка контрсделки"))
        assertTrue(out, out.contains("борker down"))
    }

    @Test
    fun `formatPostOrderSuccess paired stop uses 8-char prefix`() {
        val primary = PrimaryOutcome.Regular(orderId = "abc-1", executedLots = 1, totalLots = 1)
        val paired = PairedOutcome.Stop(instrument = gazp, orderId = "0123456789ab", quantity = 3)
        val out = formatPostOrderSuccess(primary, paired)
        assertTrue(out, out.contains("01234567…"))
    }

    // ---------- formatCostOverlay ----------

    @Test
    fun `formatCostOverlay points and domestic`() {
        val state = OrderFormState(selectedInstrument = sber)
        val details =
            TradeDetails(
                executionPrice = 0.0,
                costPoints = 10.0,
                costDomesticCurrency = 1000.0,
            )
        val out = formatCostOverlay(details, state)!!
        val parts = out.split("  ·  ")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotEmpty())
        assertTrue(parts[1].isNotEmpty())
    }

    @Test
    fun `formatCostOverlay currency only matches formatDomesticCurrency`() {
        val state = OrderFormState(selectedInstrument = sber)
        val details = TradeDetails(executionPrice = 0.0, costCurrency = 100.0)
        val out = formatCostOverlay(details, state)
        assertEquals(formatDomesticCurrency(100.0), out)
    }

    @Test
    fun `formatCostOverlay null when no cost fields`() {
        val state = OrderFormState(selectedInstrument = sber)
        val details = TradeDetails(executionPrice = 0.0)
        assertNull(formatCostOverlay(details, state))
    }

    // ---------- formatMultiplierOverlay ----------

    @Test
    fun `formatMultiplierOverlay points and domestic`() {
        val state = OrderFormState(pairedInstrument = gazp)
        val details =
            TradeDetails(
                executionPrice = 0.0,
                multiplierPoints = 5.0,
                multiplierDomesticCurrency = 500.0,
            )
        val out = formatMultiplierOverlay(details, state)!!
        val parts = out.split("  ·  ")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotEmpty())
        assertTrue(parts[1].isNotEmpty())
    }

    @Test
    fun `formatMultiplierOverlay currency only matches formatDomesticCurrency`() {
        val state = OrderFormState(pairedInstrument = gazp)
        val details = TradeDetails(executionPrice = 0.0, multiplierCurrency = 250.0)
        val out = formatMultiplierOverlay(details, state)
        assertEquals(formatDomesticCurrency(250.0), out)
    }

    @Test
    fun `formatMultiplierOverlay null when no multiplier fields`() {
        val state = OrderFormState(pairedInstrument = gazp)
        val details = TradeDetails(executionPrice = 0.0)
        assertNull(formatMultiplierOverlay(details, state))
    }

    // ---------- formatPrice branch via FUTURES ----------

    @Test
    fun `formatCostOverlay futures points uses pt suffix`() {
        val futures =
            com.gitlab.biomorf.tscalp.domain.models.Future(
                brokerName = BrokerName.TINVEST,
                tscalpInstrumentId = "uid-si",
                ticker = "SiU4",
                name = "Фьючерс Si",
                isin = "SiU4",
                instrumentType = InstrumentType.FUTURES,
            )
        val state = OrderFormState(selectedInstrument = futures)
        val details =
            TradeDetails(
                executionPrice = 0.0,
                costPoints = 10.0,
                costDomesticCurrency = 1000.0,
            )
        val out = formatCostOverlay(details, state)!!
        assertTrue(out, out.contains(" пт"))
    }
}
