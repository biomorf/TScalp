package com.gitlab.biomorf.tscalp.transport.ktor.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO для WebSocket-сообщений канала
// /tinkoff.public.invest.api.contract.v1.OrdersStreamService/OrderStateStream.
//
// Источник схемы: RussianInvestments/investAPI -> src/docs/ws/asyncapi.yaml.
// Формат: protobuf JSON mapping (lowerCamelCase, int64 -> string).

/**
 * Запрос на подписку на OrderStateStream.
 *
 * [pingDelayMillis] — интервал пинга, 1000–120000 мс (по умолчанию 120000).
 * Обратите внимание: поле называется `pingDelayMillis` (не `pingDelayMs`,
 * как в других каналах).
 */
@Serializable
data class OrderStateStreamRequestDto(
    @SerialName("accounts") val accounts: List<String>,
    @SerialName("pingDelayMillis") val pingDelayMillis: Int? = null,
)

/**
 * Ответ стрима. Ровно одно из полей не-null в каждом сообщении:
 * [orderState] — обновление заявки; [subscription] — подтверждение
 * подписки; [ping] — keepalive.
 */
@Serializable
data class OrderStateStreamResponseDto(
    @SerialName("orderState") val orderState: OrderStateDto? = null,
    @SerialName("subscription") val subscription: OrderStateSubscriptionDto? = null,
    @SerialName("ping") val ping: PingDto? = null,
)

/**
 * Заявка (поручение).
 *
 * Поля мапятся 1-в-1 в доменную `OrderState` (см. OrderModels.kt).
 * `lotsRequested`, `lotsExecuted` — int64 в proto, в JSON-маппинге строки.
 * `initialOrderPrice`, `executedOrderPrice` — MoneyValue (units string,
 * nano int, currency).
 * `createdAt` — RFC3339 string.
 */
@Serializable
data class OrderStateDto(
    @SerialName("orderId") val orderId: String = "",
    @SerialName("orderRequestId") val orderRequestId: String = "",
    @SerialName("ticker") val ticker: String = "",
    @SerialName("classCode") val classCode: String = "",
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("direction") val direction: String = "",
    @SerialName("orderType") val orderType: String = "",
    @SerialName("executionReportStatus") val executionReportStatus: String = "",
    @SerialName("initialOrderPrice") val initialOrderPrice: MoneyValueDto? = null,
    @SerialName("executedOrderPrice") val executedOrderPrice: MoneyValueDto? = null,
    @SerialName("lotsRequested") val lotsRequested: String = "0",
    @SerialName("lotsExecuted") val lotsExecuted: String = "0",
    @SerialName("createdAt") val createdAt: String? = null,
)

/**
 * Подтверждение подписки на OrderStateStream.
 *
 * Отличается от `SubscriptionsDto` у PositionsStream:
 * - `accounts` — просто список идентификаторов, без статусов по каждому;
 * - общий `status` — RESULT_SUBSCRIPTION_STATUS_OK / _ERROR;
 * - `error` — если status = ERROR.
 */
@Serializable
data class OrderStateSubscriptionDto(
    @SerialName("trackingId") val trackingId: String? = null,
    @SerialName("status") val status: String = "",
    @SerialName("streamId") val streamId: String? = null,
    @SerialName("accounts") val accounts: List<String> = emptyList(),
    @SerialName("error") val error: OrderStateSubscriptionErrorDto? = null,
)

@Serializable
data class OrderStateSubscriptionErrorDto(
    @SerialName("code") val code: String = "",
    @SerialName("message") val message: String = "",
)
