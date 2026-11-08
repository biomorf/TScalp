package com.gitlab.biomorf.tscalp.domain.models

/**
 * Identity карточки «Последние просмотренные» для persistence
 * (`CardsRepository`). Только стабильные поля; enrich-версия
 * `SelectedInstrumentInfo` (currentPrice, quantity, profit, …)
 * вычисляется в Coordinator / VM из `InstrumentRepository` +
 * `PositionFlowManager.sheet` и **не хранится**.
 *
 * **Composite identity** — `(tscalpInstrumentId, brokerName)`.
 * `tscalpInstrumentId` broker-specific (uid / figi / id), поэтому
 * один `tscalpInstrumentId` между брокерами неоднозначен.
 * `accountId` в ключ не входит: один инструмент на одном счёте
 * у одного брокера — одна карточка.
 *
 * `ticker` в identity **не хранится** (2026-11-08): domain везде
 * оперирует `tscalpInstrumentId`; `ticker` — только data-граница
 * с брокером (BCS/Finam).
 */
data class CardIdentity(
    val tscalpInstrumentId: String,
    val brokerName: BrokerName = BrokerName.TINVEST,
    val accountId: String? = null,
)

/**
 * Проверяет, относится ли карточка к инструменту с заданным
 * composite-ключом `(tscalpInstrumentId, brokerName)`.
 *
 * **Composite-ключ** — `tscalpInstrumentId` broker-specific
 * (uid / figi / id), поэтому один `tscalpInstrumentId` между брокерами
 * неоднозначен. `accountId` в ключ не входит: один инструмент на
 * одном счёте у одного брокера — одна карточка (роли primary/pair
 * не различаются).
 */
fun CardIdentity.matches(
    tscalpInstrumentId: String,
    brokerName: BrokerName,
): Boolean = this.tscalpInstrumentId == tscalpInstrumentId && this.brokerName == brokerName

/** То же, что [matches] с ключом из [Instrument]. */
fun CardIdentity.matches(instrument: Instrument): Boolean = matches(instrument.tscalpInstrumentId, instrument.brokerName)

/** Находит карточку по composite-ключу (см. [matches]); `null` — нет. */
fun List<CardIdentity>.findCard(
    tscalpInstrumentId: String,
    brokerName: BrokerName,
): CardIdentity? = find { it.matches(tscalpInstrumentId, brokerName) }

/** То же, что [findCard] с ключом из [Instrument]. */
fun List<CardIdentity>.findCard(instrument: Instrument): CardIdentity? = findCard(instrument.tscalpInstrumentId, instrument.brokerName)

/**
 * Возвращает копию списка, где карточка с `(tscalpInstrumentId, brokerName)`
 * получает новый `accountId`; остальные карточки не меняются.
 *
 * Pure, идемпотентна: повторный вызов с теми же аргументами даёт тот же
 * список. `ticker` не участвует (см. [matches]).
 */
fun List<CardIdentity>.withUpdatedSettings(
    tscalpInstrumentId: String,
    brokerName: BrokerName,
    accountId: String?,
): List<CardIdentity> =
    map { card ->
        if (card.matches(tscalpInstrumentId, brokerName)) {
            card.copy(accountId = accountId)
        } else {
            card
        }
    }
