package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.transport.ktor.dto.LastPriceDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.MarketDataResponseDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PingDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.QuotationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TInvestPricesWsMapperTest {
    @Test
    fun `valid lastPrice maps to uid and value`() {
        val response =
            MarketDataResponseDto(
                lastPrice =
                    LastPriceDto(
                        instrumentUid = "uid-sber",
                        ticker = "SBER",
                        price = QuotationDto(units = "292", nano = 600_000_000),
                    ),
            )

        val result = TInvestPricesWsMapper.mapLastPrice(response)

        assertEquals("uid-sber", result?.first)
        assertEquals(292.6, result!!.second, 0.0001)
    }

    @Test
    fun `lastPrice without price returns null`() {
        val response =
            MarketDataResponseDto(
                lastPrice =
                    LastPriceDto(
                        instrumentUid = "uid-sber",
                        price = null,
                    ),
            )

        assertNull(TInvestPricesWsMapper.mapLastPrice(response))
    }

    @Test
    fun `lastPrice with blank uid returns null`() {
        val response =
            MarketDataResponseDto(
                lastPrice =
                    LastPriceDto(
                        instrumentUid = "",
                        price = QuotationDto(units = "100", nano = 0),
                    ),
            )

        assertNull(TInvestPricesWsMapper.mapLastPrice(response))
    }

    @Test
    fun `ping only returns null`() {
        val response =
            MarketDataResponseDto(
                ping = PingDto(time = "2026-10-21T10:00:00Z"),
            )

        assertNull(TInvestPricesWsMapper.mapLastPrice(response))
    }

    @Test
    fun `subscription confirmation without lastPrice returns null`() {
        val response = MarketDataResponseDto(lastPrice = null)

        assertNull(TInvestPricesWsMapper.mapLastPrice(response))
    }
}
