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
 * **полным composite-ключом** `(tscalpInstrumentId, brokerName, accountId)`.
 *
 * **Composite-ключ** — все три поля. `(uid, T, A)` и `(uid, T, B)` —
 * **разные** карточки (один инструмент у одного брокера на разных
 * счетах). `(uid, T, null)` и `(uid, T, A)` — тоже **разные**
 * (карточка без выбранного счёта vs с выбранным). См.
 * `docs/architecture.md` → «CardIdentity и слоты».
 */
fun CardIdentity.matches(
    tscalpInstrumentId: String,
    brokerName: BrokerName,
    accountId: String?,
): Boolean =
    this.tscalpInstrumentId == tscalpInstrumentId &&
        this.brokerName == brokerName &&
        this.accountId == accountId

/** То же, что [matches], с ключом из [Instrument] + явный `accountId`. */
fun CardIdentity.matches(
    instrument: Instrument,
    accountId: String?,
): Boolean = matches(instrument.tscalpInstrumentId, instrument.brokerName, accountId)

/** Находит карточку по полному composite-ключу (см. [matches]); `null` — нет. */
fun List<CardIdentity>.findCard(
    tscalpInstrumentId: String,
    brokerName: BrokerName,
    accountId: String?,
): CardIdentity? = find { it.matches(tscalpInstrumentId, brokerName, accountId) }

/** То же, что [findCard], с ключом из [Instrument] + явный `accountId`. */
fun List<CardIdentity>.findCard(
    instrument: Instrument,
    accountId: String?,
): CardIdentity? = findCard(instrument.tscalpInstrumentId, instrument.brokerName, accountId)

/**
 * Возвращает копию списка, где карточка с `(uid, broker, oldAccountId)`
 * **удаляется**, а карточка с `(uid, broker, newAccountId)` **создаётся**
 * (с тем же `tscalpInstrumentId` / `brokerName`). Остальные карточки не
 * меняются.
 *
 * **Пересоздание identity**: `accountId` — часть ключа, менять его
 * «на месте» нельзя (иначе `(uid, broker, A)` и `(uid, broker, B)` слиплись
 * бы). Если `oldAccountId == null` — просто добавляется новая карточка
 * `(uid, broker, newAccountId)`.
 *
 * Pure, идемпотентна: повторный вызов с теми же аргументами даёт тот же
 * список. Коллизию (`newAccountId` уже занят другой карточкой) —
 * **не** разрешает; `SaveCardSettingsUseCase` проверяет её до вызова и
 * возвращает `Failure`.
 */
fun List<CardIdentity>.withRecreatedSettings(
    tscalpInstrumentId: String,
    brokerName: BrokerName,
    oldAccountId: String?,
    newAccountId: String?,
): List<CardIdentity> {
    val withoutOld = filterNot { it.matches(tscalpInstrumentId, brokerName, oldAccountId) }
    val alreadyHasNew = withoutOld.any { it.matches(tscalpInstrumentId, brokerName, newAccountId) }
    return if (alreadyHasNew) {
        withoutOld
    } else {
        withoutOld + CardIdentity(tscalpInstrumentId, brokerName, newAccountId)
    }
}

// ---------- Deprecated overloads (key without accountId) ----------
// Используются в callsites Фазы 1a, пока не мигрированы (1b-1b..1b-6).
// Удаляются в 1b-7. Поведение — любая карточка с этим (uid, broker).

@Deprecated("Use matches(tscalpInstrumentId, brokerName, accountId)")
fun CardIdentity.matches(
    tscalpInstrumentId: String,
    brokerName: BrokerName,
): Boolean = this.tscalpInstrumentId == tscalpInstrumentId && this.brokerName == brokerName

@Deprecated("Use matches(instrument, accountId)")
fun CardIdentity.matches(instrument: Instrument): Boolean = matches(instrument.tscalpInstrumentId, instrument.brokerName)

@Deprecated("Use findCard(tscalpInstrumentId, brokerName, accountId)")
fun List<CardIdentity>.findCard(
    tscalpInstrumentId: String,
    brokerName: BrokerName,
): CardIdentity? = find { it.matches(tscalpInstrumentId, brokerName) }

@Deprecated("Use findCard(instrument, accountId)")
fun List<CardIdentity>.findCard(instrument: Instrument): CardIdentity? = findCard(instrument.tscalpInstrumentId, instrument.brokerName)

@Deprecated("Use withRecreatedSettings(uid, broker, oldAccountId, newAccountId)")
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
