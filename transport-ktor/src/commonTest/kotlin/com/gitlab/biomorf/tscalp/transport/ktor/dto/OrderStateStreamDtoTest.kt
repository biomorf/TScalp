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
// ВАЖНО: реальный формат подтвердить на первом живом
// WS-подключении к OrderStateStream.

class OrderStateStreamDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `encodes order state stream request`() {
        val request =
            OrderStateStreamRequestDto(
                accounts = listOf("acc-1", "acc-2"),
                pingDelayMillis = 60_000,
            )

        val encoded = json.encodeToString(OrderStateStreamRequestDto.serializer(), request)

        assertTrue(encoded.contains("\"accounts\":[\"acc-1\",\"acc-2\"]"))
        assertTrue(encoded.contains("\"pingDelayMillis\":60000"))
    }

    @Test
    fun `parses subscription OK`() {
        val raw = """
            {
              "subscription": {
                "trackingId": "track-1",
                "status": "RESULT_SUBSCRIPTION_STATUS_OK",
                "streamId": "stream-1",
                "accounts": ["acc-1"]
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(OrderStateStreamResponseDto.serializer(), raw)

        val sub = dto.subscription
        assertNotNull(sub)
        assertEquals("RESULT_SUBSCRIPTION_STATUS_OK", sub.status)
        assertEquals("track-1", sub.trackingId)
        assertEquals(listOf("acc-1"), sub.accounts)
        assertNull(sub.error)
        assertNull(dto.orderState)
        assertNull(dto.ping)
    }

    @Test
    fun `parses subscription with error`() {
        val raw = """
            {
              "subscription": {
                "status": "RESULT_SUBSCRIPTION_STATUS_ERROR",
                "accounts": ["acc-1"],
                "error": {
                  "code": "UNAUTHENTICATED",
                  "message": "Token is invalid"
                }
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(OrderStateStreamResponseDto.serializer(), raw)

        val sub = dto.subscription
        assertNotNull(sub)
        assertEquals("RESULT_SUBSCRIPTION_STATUS_ERROR", sub.status)
        assertNotNull(sub.error)
        assertEquals("UNAUTHENTICATED", sub.error!!.code)
        assertEquals("Token is invalid", sub.error!!.message)
    }

    @Test
    fun `parses order state update`() {
        val raw = """
            {
              "orderState": {
                "orderId": "order-1",
                "orderRequestId": "req-1",
                "ticker": "SBER",
                "classCode": "TQBR",
                "direction": "ORDER_DIRECTION_BUY",
                "executionReportStatus": "EXECUTION_REPORT_STATUS_NEW",
                "initialOrderPrice": {
                  "units": "290",
                  "nano": 500000000,
                  "currency": "rub"
                },
                "executedOrderPrice": null,
                "lotsRequested": "10",
                "lotsExecuted": "0",
                "createdAt": "2026-10-21T10:00:00Z"
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString(OrderStateStreamResponseDto.serializer(), raw)

        val o = dto.orderState
        assertNotNull(o)
        assertEquals("order-1", o.orderId)
        assertEquals("req-1", o.orderRequestId)
        assertEquals("SBER", o.ticker)
        assertEquals("ORDER_DIRECTION_BUY", o.direction)
        assertEquals("EXECUTION_REPORT_STATUS_NEW", o.executionReportStatus)
        assertNotNull(o.initialOrderPrice)
        assertEquals("290", o.initialOrderPrice!!.units)
        assertEquals(500_000_000, o.initialOrderPrice!!.nano)
        assertEquals("rub", o.initialOrderPrice!!.currency)
        assertNull(o.executedOrderPrice)
        assertEquals("10", o.lotsRequested)
        assertEquals("0", o.lotsExecuted)
        assertEquals("2026-10-21T10:00:00Z", o.createdAt)
    }

    @Test
    fun `unknown fields are ignored`() {
        val raw = """
            {
              "orderState": {
                "orderId": "order-1",
                "unknownField": "ignored"
              },
              "unknownTopLevel": "ignored"
            }
        """.trimIndent()

        val dto = json.decodeFromString(OrderStateStreamResponseDto.serializer(), raw)

        assertNotNull(dto.orderState)
        assertEquals("order-1", dto.orderState!!.orderId)
    }
}
