package com.gitlab.biomorf.tscalp.transport.ktor.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO для WebSocket-сообщений канала
// /tinkoff.public.invest.api.contract.v1.MarketDataStreamService/MarketDataStream.
//
// Источник схемы: RussianInvestments/investAPI -> src/docs/ws/asyncapi.yaml.
// Формат: protobuf JSON mapping (lowerCamelCase, int64 -> string).

/**
 * Запрос подписки на MarketDataStream (bidirectional stream).
 *
 * Обёртка над операциями: подписка на lastPrice, свечи, стакан, сделки,
 * торговый статус. В текущей реализации используется только lastPrice
 * (subscribeLastPriceRequest), остальные поля отсутствуют.
 *
 * Отправляется как один кадр сразу после установки соединения.
 */
@Serializable
data class MarketDataRequestDto(
    @SerialName("subscribeLastPriceRequest") val subscribeLastPriceRequest: SubscribeLastPriceRequestDto? = null,
    @SerialName("pingSettings") val pingSettings: PingDelaySettingsDto? = null,
)

/**
 * Запрос на подписку/отписку на цену последней сделки.
 *
 * [subscriptionAction] — SUBSCRIBE или UNSUBSCRIBE, см. SubscriptionActionDto.
 */
@Serializable
data class SubscribeLastPriceRequestDto(
    @SerialName("subscriptionAction") val subscriptionAction: String,
    @SerialName("instruments") val instruments: List<LastPriceInstrumentDto>,
)

/**
 * Инструмент в подписке на lastPrice.
 *
 * [instrumentId] принимает figi, instrument_uid или ticker_classCode.
 * В проекте используется tscalpInstrumentId (= instrument_uid у T-Invest).
 */
@Serializable
data class LastPriceInstrumentDto(
    @SerialName("instrumentId") val instrumentId: String,
)

/**
 * Ответ стрима. Ровно одно из полей не-null в каждом сообщении.
 *
 * [subscribeLastPriceResponse] — подтверждение подписки;
 * [lastPrice] — обновление цены;
 * [ping] — keepalive.
 */
@Serializable
data class MarketDataResponseDto(
    @SerialName("subscribeLastPriceResponse") val subscribeLastPriceResponse: SubscribeLastPriceResponseDto? = null,
    @SerialName("lastPrice") val lastPrice: LastPriceDto? = null,
    @SerialName("ping") val ping: PingDto? = null,
)

/**
 * Подтверждение подписки на lastPrice.
 */
@Serializable
data class SubscribeLastPriceResponseDto(
    @SerialName("lastPriceSubscriptions") val lastPriceSubscriptions: List<LastPriceSubscriptionDto> = emptyList(),
    @SerialName("trackingId") val trackingId: String? = null,
)

/**
 * Статус подписки на конкретный инструмент.
 *
 * [subscriptionStatus] — SUBSCRIPTION_STATUS_SUCCESS при успехе,
 * иначе код ошибки (INSTRUMENT_NOT_FOUND, LIMIT_IS_EXCEEDED и т.д.).
 */
@Serializable
data class LastPriceSubscriptionDto(
    @SerialName("figi") val figi: String = "",
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("ticker") val ticker: String = "",
    @SerialName("classCode") val classCode: String = "",
    @SerialName("subscriptionStatus") val subscriptionStatus: String = "",
    @SerialName("subscriptionId") val subscriptionId: String? = null,
    @SerialName("streamId") val streamId: String? = null,
)

/**
 * Цена последней сделки.
 *
 * [price] — Quotation (units + nano). Оба поля int64/int32 в proto,
 * но units в JSON-маппинге строка.
 */
@Serializable
data class LastPriceDto(
    @SerialName("figi") val figi: String = "",
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("ticker") val ticker: String = "",
    @SerialName("classCode") val classCode: String = "",
    @SerialName("price") val price: QuotationDto? = null,
    @SerialName("time") val time: String? = null,
    @SerialName("lastPriceType") val lastPriceType: String? = null,
)

/**
 * Котировка — денежная сумма без указания валюты.
 * units — int64 в proto, в JSON-маппинге строка. nano — int32, число.
 */
@Serializable
data class QuotationDto(
    @SerialName("units") val units: String = "0",
    @SerialName("nano") val nano: Int = 0,
)