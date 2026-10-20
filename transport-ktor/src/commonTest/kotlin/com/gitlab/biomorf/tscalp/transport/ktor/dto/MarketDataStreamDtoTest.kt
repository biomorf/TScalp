package com.gitlab.biomorf.tscalp.transport.ktor.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Fixtures — гипотетические JSON по структуре из
// RussianInvestments/investAPI -> src/docs/ws/asyncapi.yaml.
// Формат — protobuf JSON mapping (lowerCamelCase, int64 -> string).
//
// ВАЖНО: реальный формат подписки и ответов подтвердить
// на первом живом WS-подключении (десктоп).
// Если поля отличаются — поправить DTO и эти fixtures.

class MarketDataStreamDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `encodes subscribe last price request`() {
        val request =
            MarketDataRequestDto(
                subscribeLastPriceRequest =
                    SubscribeLastPriceRequestDto(
                        subscriptionAction = "SUBSCRIPTION_ACTION_SUBSCRIBE",
                        instruments =
                            listOf(
                                LastPriceInstrumentDto(instrumentId = "uid-sber"),
                                LastPriceInstrumentDto(instrumentId = "uid-gazp"),
                            ),
                    ),
            )

        val encoded = json.encodeToString(MarketDataRequestDto.serializer(), request)

        assertTrue(encoded.contains("\"subscribeLastPriceRequest\""))
        assertTrue(encoded.contains("\"subscriptionAction\":\"SUBSCRIPTION_ACTION_SUBSCRIBE\""))
        assertTrue(encoded.contains("\"instrumentId\":\"uid-sber\""))
        assertTrue(encoded.contains("\"instrumentId\":\"uid-gazp\""))
    }

    @Test
    fun `parses subscribe last price response`() {
        val raw = """
            {
              "subscribeLastPriceResponse": {
                "trackingId": "track-1",
                "lastPriceSubscriptions": [
                  {
                    "figi": "BBG004730N88",
                    "instrumentUid": "uid-sber",
                    "ticker": "SBER",
                    "classCode": "TQBR",
                    "subscriptionStatus": "SUBSCRIPTION_STATUS_SUCCESS",
                    "subscriptionId": "sub-1",
                    "streamId": "stream-1"
                  }
                ]
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(MarketDataResponseDto.serializer(), raw)

        val confirmation = dto.subscribeLastPriceResponse
        assertNotNull(confirmation)
        assertEquals("track-1", confirmation.trackingId)
        assertEquals(1, confirmation.lastPriceSubscriptions.size)
        val sub = confirmation.lastPriceSubscriptions[0]
        assertEquals("uid-sber", sub.instrumentUid)
        assertEquals("SUBSCRIPTION_STATUS_SUCCESS", sub.subscriptionStatus)
        assertNull(dto.lastPrice)
        assertNull(dto.ping)
    }

    @Test
    fun `parses last price update`() {
        val raw = """
            {
              "lastPrice": {
                "figi": "BBG004730N88",
                "instrumentUid": "uid-sber",
                "ticker": "SBER",
                "classCode": "TQBR",
                "price": { "units": "292", "nano": 600000000 },
                "time": "2026-10-21T10:00:00Z",
                "lastPriceType": "LAST_PRICE_EXCHANGE"
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(MarketDataResponseDto.serializer(), raw)

        val lp = dto.lastPrice
        assertNotNull(lp)
        assertEquals("uid-sber", lp.instrumentUid)
        assertEquals("SBER", lp.ticker)
        assertNotNull(lp.price)
        assertEquals("292", lp.price.units)
        assertEquals(600_000_000, lp.price.nano)
        assertEquals("LAST_PRICE_EXCHANGE", lp.lastPriceType)
        assertNull(dto.subscribeLastPriceResponse)
        assertNull(dto.ping)
    }

    @Test
    fun `parses ping`() {
        val raw = """
            {
              "ping": {
                "time": "2026-10-21T10:00:00Z",
                "streamId": "stream-1"
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(MarketDataResponseDto.serializer(), raw)

        assertNotNull(dto.ping)
        assertEquals("2026-10-21T10:00:00Z", dto.ping.time)
    }

    @Test
    fun `unknown fields are ignored`() {
        val raw = """
            {
              "lastPrice": {
                "instrumentUid": "uid-sber",
                "price": { "units": "100", "nano": 0 },
                "unknownField": "ignored"
              },
              "unknownTopLevel": "ignored"
            }
        """.trimIndent()

        val dto = json.decodeFromString(MarketDataResponseDto.serializer(), raw)

        assertNotNull(dto.lastPrice)
        assertEquals("uid-sber", dto.lastPrice.instrumentUid)
    }
}
