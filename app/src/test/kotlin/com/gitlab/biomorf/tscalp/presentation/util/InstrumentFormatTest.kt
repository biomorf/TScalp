package com.gitlab.biomorf.tscalp.presentation.util

import com.gitlab.biomorf.tscalp.domain.models.Share
import org.junit.Assert.assertEquals
import org.junit.Test

class InstrumentFormatTest {
    @Test
    fun `displayName combines ticker and name`() {
        val instrument =
            Share(
                tscalpInstrumentId = "uid-sber",
                ticker = "SBER",
                name = "Сбербанк",
                isin = "RU0009029540",
            )
        assertEquals("SBER - Сбербанк", instrument.displayName())
    }

    @Test
    fun `displayName with empty name keeps dash`() {
        val instrument =
            Share(
                tscalpInstrumentId = "uid-x",
                ticker = "XYZ",
                name = "",
                isin = "RU0000000001",
            )
        assertEquals("XYZ - ", instrument.displayName())
    }

    @Test
    fun `displayName with spaces in name keeps as-is`() {
        val instrument =
            Share(
                tscalpInstrumentId = "uid-gazp",
                ticker = "GAZP",
                name = "Газпром ао",
                isin = "RU0007661625",
            )
        assertEquals("GAZP - Газпром ао", instrument.displayName())
    }
}
