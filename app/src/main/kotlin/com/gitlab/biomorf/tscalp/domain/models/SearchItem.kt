package com.gitlab.biomorf.tscalp.domain.models

/**
 * Результат поиска инструментов: partial-модель для search UI.
 *
 * T-6 (variant A): search-путь возвращает SearchItem вместо full Instrument
 * (1 unary findInstrument вместо 1 + N getInstrumentBy). Full Instrument
 * загружается lazy на select через SelectInstrumentUseCase
 * (InstrumentRepository.getInstrument).
 *
 * Поля — только те, что нужны search UI: ticker, name, instrumentType
 * (SearchResultItem); plus идентификаторы (tscalpInstrumentId, brokerName)
 * и optional figi/classCode.
 */
data class SearchItem(
    val tscalpInstrumentId: String,
    val brokerName: BrokerName,
    val ticker: String,
    val name: String,
    val instrumentType: InstrumentType,
    val figi: String? = null,
    val classCode: String? = null,
)
