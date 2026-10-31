package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.transport.ktor.dto.MoneyValueDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsFuturesDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsResponseDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsSecuritiesDto
import com.gitlab.biomorf.tscalp.transport.ktor.dto.PositionsStreamResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TInvestPositionsWsMapperTest {
    // ---------- money / blocked ----------

    @Test
    fun `money only emits one currency item without blocked`() {
        val response =
            snapshot(
                money = listOf(MoneyValueDto(currency = "rub", units = "2415", nano = 310_000_000)),
                blocked = emptyList(),
            )

        val result = TInvestPositionsWsMapper.mapAll(response)

        assertEquals(1, result.size)
        val item = result.single()
        assertEquals("currency", item.instrumentType)
        assertEquals("", item.tscalpInstrumentId)
        assertEquals("rub", item.currency)
        assertEquals(2415.31, item.money!!, 0.0001)
        assertNull(item.blocked)
    }

    @Test
    fun `money and blocked of same currency merge into one item`() {
        val response =
            snapshot(
                money = listOf(MoneyValueDto(currency = "rub", units = "2415", nano = 310_000_000)),
                blocked = listOf(MoneyValueDto(currency = "rub", units = "100", nano = 0)),
            )

        val result = TInvestPositionsWsMapper.mapAll(response)

        assertEquals(1, result.size)
        val item = result.single()
        assertEquals("rub", item.currency)
        assertEquals(2415.31, item.money!!, 0.0001)
        assertEquals(100.0, item.blocked!!, 0.0001)
    }

    @Test
    fun `multiple currencies emit separate items`() {
        val response =
            snapshot(
                money =
                    listOf(
                        MoneyValueDto(currency = "rub", units = "100", nano = 0),
                        MoneyValueDto(currency = "usd", units = "50", nano = 0),
                    ),
                blocked =
                    listOf(
                        MoneyValueDto(currency = "usd", units = "10", nano = 0),
                    ),
            )

        val result = TInvestPositionsWsMapper.mapAll(response)

        assertEquals(2, result.size)
        val rub = result.single { it.currency == "rub" }
        val usd = result.single { it.currency == "usd" }
        assertEquals(100.0, rub.money!!, 0.0001)
        assertNull(rub.blocked)
        assertEquals(50.0, usd.money!!, 0.0001)
        assertEquals(10.0, usd.blocked!!, 0.0001)
    }

    @Test
    fun `empty money and blocked emit nothing`() {
        val response = snapshot(money = emptyList(), blocked = emptyList())

        val result = TInvestPositionsWsMapper.mapAll(response)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `blank currency is skipped`() {
        val response =
            snapshot(
                money = listOf(MoneyValueDto(currency = "", units = "100", nano = 0)),
                blocked = emptyList(),
            )

        val result = TInvestPositionsWsMapper.mapAll(response)

        assertTrue(result.isEmpty())
    }

    // ---------- securities / futures не ломаются ----------

    @Test
    fun `securities and money coexist without interference`() {
        val response =
            snapshot(
                money = listOf(MoneyValueDto(currency = "rub", units = "2415", nano = 310_000_000)),
                blocked = emptyList(),
                securities =
                    listOf(
                        PositionsSecuritiesDto(
                            instrumentUid = "uid-sber",
                            ticker = "SBER",
                            classCode = "TQBR",
                            instrumentType = "share",
                            balance = "2",
                        ),
                    ),
            )

        val result = TInvestPositionsWsMapper.mapAll(response)

        assertEquals(2, result.size)
        assertTrue(result.any { it.tscalpInstrumentId == "uid-sber" && it.instrumentType == "share" })
        assertTrue(result.any { it.instrumentType == "currency" && it.currency == "rub" })
    }

    // ---------- helpers ----------

    private fun snapshot(
        money: List<MoneyValueDto> = emptyList(),
        blocked: List<MoneyValueDto> = emptyList(),
        securities: List<PositionsSecuritiesDto> = emptyList(),
        futures: List<PositionsFuturesDto> = emptyList(),
    ) = PositionsStreamResponseDto(
        initialPositions =
            PositionsResponseDto(
                accountId = "test-account",
                money = money,
                blocked = blocked,
                securities = securities,
                futures = futures,
            ),
    )
}
