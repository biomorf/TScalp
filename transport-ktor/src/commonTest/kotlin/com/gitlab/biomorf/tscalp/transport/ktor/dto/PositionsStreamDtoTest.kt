package com.gitlab.biomorf.tscalp.transport.ktor.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Fixtures — гипотетические JSON по структуре из
// RussianInvestments/investAPI → src/docs/ws/asyncapi.yaml.
// Формат — protobuf JSON mapping (lowerCamelCase, int64 → string).
//
// ВАЖНО: точный формат подписки и реальные поля ответа
// подтвердим на первом живом WS-подключении (десктоп).
// Если поля отличаются — поправить DTO и эти fixtures.

class PositionsStreamDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses initialPositions with securities and futures`() {
        val raw = """
            {
              "initialPositions": {
                "accountId": "acc-1",
                "securities": [
                  {
                    "figi": "BBG004730N88",
                    "instrumentUid": "uid-sber",
                    "ticker": "SBER",
                    "classCode": "TQBR",
                    "instrumentType": "share",
                    "balance": "10"
                  }
                ],
                "futures": [
                  {
                    "figi": "FUT-SI",
                    "instrumentUid": "uid-si",
                    "ticker": "SiU4",
                    "classCode": "SPBFUT",
                    "balance": "2"
                  }
                ]
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(PositionsStreamResponseDto.serializer(), raw)

        assertNotNull(dto.initialPositions)
        assertEquals("acc-1", dto.initialPositions.accountId)
        assertEquals(1, dto.initialPositions.securities.size)
        assertEquals("SBER", dto.initialPositions.securities[0].ticker)
        assertEquals("10", dto.initialPositions.securities[0].balance)
        assertEquals(1, dto.initialPositions.futures.size)
        assertEquals("SiU4", dto.initialPositions.futures[0].ticker)
        assertNull(dto.position)
        assertNull(dto.ping)
    }

    @Test
    fun `parses position update`() {
        val raw = """
            {
              "position": {
                "accountId": "acc-1",
                "securities": [
                  {
                    "instrumentUid": "uid-sber",
                    "ticker": "SBER",
                    "classCode": "TQBR",
                    "balance": "15"
                  }
                ],
                "futures": []
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(PositionsStreamResponseDto.serializer(), raw)

        assertNotNull(dto.position)
        assertEquals("acc-1", dto.position.accountId)
        assertEquals(1, dto.position.securities.size)
        assertEquals("15", dto.position.securities[0].balance)
        assertNull(dto.initialPositions)
        assertNull(dto.ping)
    }

    @Test
    fun `parses ping`() {
        val raw = """
            {
              "ping": {
                "time": "2026-10-17T14:00:00Z"
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(PositionsStreamResponseDto.serializer(), raw)

        assertNotNull(dto.ping)
        assertEquals("2026-10-17T14:00:00Z", dto.ping.time)
        assertNull(dto.initialPositions)
        assertNull(dto.position)
    }

    @Test
    fun `ignores unknown fields in response`() {
        val raw = """
            {
              "position": {
                "accountId": "acc-1",
                "securities": [],
                "futures": [],
                "someFutureField": "ignored"
              },
              "unknownTopLevel": 42
            }
        """.trimIndent()

        val dto = json.decodeFromString(PositionsStreamResponseDto.serializer(), raw)

        assertNotNull(dto.position)
        assertEquals("acc-1", dto.position.accountId)
    }

    @Test
    fun `parses balance as string not as long`() {
        val raw = """
            {
              "initialPositions": {
                "accountId": "acc-1",
                "securities": [
                  { "instrumentUid": "uid", "ticker": "SBER", "balance": "1229583" }
                ]
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(PositionsStreamResponseDto.serializer(), raw)

        val balance = dto.initialPositions?.securities?.first()?.balance
        assertEquals("1229583", balance)
    }
}
