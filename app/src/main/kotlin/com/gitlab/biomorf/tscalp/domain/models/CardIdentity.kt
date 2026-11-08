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
/**
 * Возвращает копию списка, где карточка `(uid, broker, newAccountId)`
 * **гарантированно присутствует**. Если её нет — добавляется в конец;
 * если есть — список возвращается без изменений (idempotent add).
 *
 * **Модель A (1b-11):** `CardIdentity` — запись истории «Последних
 * просмотренных». Один `(uid, broker)` может иметь много записей
 * (разные счета = разные карточки). Смена счёта в слоте — не
 * «пересоздание» (delete old + add new), а «ensure target exists»:
 * старая запись **остаётся** в истории.
 *
 * Pure, идемпотентна. `null` — валидный `newAccountId` (карточка без
 * счёта); ensure с `null` тоже no-op, если `(uid, broker, null)` уже есть.
 */
fun List<CardIdentity>.withEnsuredCard(
    tscalpInstrumentId: String,
    brokerName: BrokerName,
    newAccountId: String?,
): List<CardIdentity> {
    val alreadyHas = any { it.matches(tscalpInstrumentId, brokerName, newAccountId) }
    return if (alreadyHas) {
        this
    } else {
        this + CardIdentity(tscalpInstrumentId, brokerName, newAccountId)
    }
}

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
