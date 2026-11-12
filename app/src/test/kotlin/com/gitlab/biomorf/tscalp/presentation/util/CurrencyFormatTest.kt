package com.gitlab.biomorf.tscalp.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [formatCurrency]. Guards against ISO 4217 case
 * regressions: `Currency.getInstance("rub")` would throw
 * `IllegalArgumentException`; normalisation to uppercase fixes that.
 */
class CurrencyFormatTest {
    @Test
    fun `lowercase currency matches uppercase`() {
        assertEquals(formatCurrency(1234.5, "RUB"), formatCurrency(1234.5, "rub"))
    }

    @Test
    fun `mixed case currency matches uppercase`() {
        assertEquals(formatCurrency(1234.5, "RUB"), formatCurrency(1234.5, "Rub"))
    }

    @Test
    fun `uppercase currency renders non-empty`() {
        val result = formatCurrency(1234.5, "RUB")
        assertTrue("expected non-empty, got '$result'", result.isNotEmpty())
    }

    @Test
    fun `usd lowercase matches uppercase`() {
        assertEquals(formatCurrency(1234.5, "USD"), formatCurrency(1234.5, "usd"))
    }
}
