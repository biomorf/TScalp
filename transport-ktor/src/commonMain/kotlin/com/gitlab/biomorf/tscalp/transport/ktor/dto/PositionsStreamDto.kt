package com.gitlab.biomorf.tscalp.transport.ktor.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO для WebSocket-сообщений канала
// /tinkoff.public.invest.api.contract.v1.OperationsStreamService/PositionsStream.
//
// Источник схемы: RussianInvestments/investAPI → src/docs/ws/asyncapi.yaml.
// Формат: protobuf JSON mapping (lowerCamelCase, int64 → string).

/**
 * Подписка на PositionsStream.
 *
 * ВНИМАНИЕ: точная структура конверта подписки (есть ли внешний
 * ключ "positionsStream" или поля идут плоско) в спецификации не
 * приведена. Гипотеза — обёртка с именем канала или метода.
 * Проверить при первой реализации отправки.
 */
@Serializable
data class PositionsStreamSubscribeRequestDto(
    @SerialName("accounts") val accounts: List<String>,
    @SerialName("withInitialPositions") val withInitialPositions: Boolean = true,
    @SerialName("pingSettings") val pingSettings: PingDelaySettingsDto? = null,
)

@Serializable
data class PingDelaySettingsDto(
    @SerialName("pingDelayMs") val pingDelayMs: Int? = null,
)

/**
 * Ответ стрима. Ровно одно из полей будет не-null в каждом сообщении.
 */
@Serializable
data class PositionsStreamResponseDto(
    @SerialName("initialPositions") val initialPositions: PositionsResponseDto? = null,
    @SerialName("position") val position: PositionDataDto? = null,
    @SerialName("ping") val ping: PingDto? = null,
)

/**
 * Снапшот позиций при подключении (withInitialPositions = true).
 */
@Serializable
data class PositionsResponseDto(
    @SerialName("accountId") val accountId: String = "",
    @SerialName("securities") val securities: List<PositionsSecuritiesDto> = emptyList(),
    @SerialName("futures") val futures: List<PositionsFuturesDto> = emptyList(),
)

/**
 * Изменения позиций.
 */
@Serializable
data class PositionDataDto(
    @SerialName("accountId") val accountId: String = "",
    @SerialName("securities") val securities: List<PositionsSecuritiesDto> = emptyList(),
    @SerialName("futures") val futures: List<PositionsFuturesDto> = emptyList(),
    @SerialName("date") val date: String? = null,
)

@Serializable
data class PositionsSecuritiesDto(
    @SerialName("figi") val figi: String = "",
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("ticker") val ticker: String = "",
    @SerialName("classCode") val classCode: String = "",
    @SerialName("instrumentType") val instrumentType: String = "",
    // Гипотеза: protobuf JSON mapping передаёт int64 как string.
    // Если окажется long — поправить на Long?.
    @SerialName("balance") val balance: String? = null,
)

@Serializable
data class PositionsFuturesDto(
    @SerialName("figi") val figi: String = "",
    @SerialName("instrumentUid") val instrumentUid: String = "",
    @SerialName("ticker") val ticker: String = "",
    @SerialName("classCode") val classCode: String = "",
    @SerialName("balance") val balance: String? = null,
)

/**
 * Ping от сервера. Поле time — protobuf Timestamp в JSON
 * (RFC3339 string). Для health-check достаточно факта получения.
 */
@Serializable
data class PingDto(
    @SerialName("time") val time: String? = null,
)
