package com.gitlab.biomorf.tscalp.domain.models

/**
 * Элемент потока позиций — событие от одного из источников
 * (gRPC stream, WS, polling).
 *
 * Может быть **полным** (от polling, все поля заполнены) или
 * **неполным** (от стрима — только количество и идентификаторы,
 * без P&L). Обогащение неполных событий полями из кэша полных —
 * задача `PositionFlowManager.enrichAndCache`.
 *
 * @property tscalpInstrumentId универсальный ключ инструмента
 *   (не `uid`/`figi`). Пустой для валютных позиций из WS
 *   (`instrumentType = "currency"`) — там только `currency`,
 *   склейка с polling по `currency`.
 * @property brokerName брокер-источник события.
 * @property ticker биржевой тикер (`SBER`, `TMON@`). Пустой
 *   для валютных позиций из WS.
 * @property quantity количество (balance, лоты/единицы).
 * @property currentPrice текущая цена. `null` — событие
 *   неполное (от стрима). Непустой (даже `0.0`) — маркер
 *   полного события от polling.
 * @property averagePositionPrice средняя цена позиции. `null`
 *   в неполных событиях; заполняется из polling.
 * @property expectedYield P&L от брокера (в рублях). `null`
 *   в неполных событиях; заполняется из polling.
 * @property classCode код режима торгов (`TQBR`, `SPBRU`).
 * @property isin ISIN инструмента.
 * @property moneyPerPointOfPrice стоимость шага цены (только фьючерсы).
 * @property instrumentType строковый тип: `"share"`, `"futures"`,
 *   `"currency"`, `"etf"`.
 * @property money для валютных позиций: свободный остаток.
 *   См. inline-комментарий ниже — формула связи с `blocked`
 *   и `quantity` не подтверждена.
 * @property blocked для валютных позиций: заблокированный
 *   остаток. См. inline-комментарий ниже.
 * @property currency ISO-4217 код валюты (`"rub"`). Только
 *   для `instrumentType = "currency"`.
 */
data class PositionStreamItem(
    // Обязательные: без них позицию нельзя идентифицировать
    val tscalpInstrumentId: String,
    val brokerName: BrokerName,
    val ticker: String,
    val quantity: Long,
    // Опциональные: приходят не всегда
    val currentPrice: Double? = null,
    val averagePositionPrice: Double? = null,
    val expectedYield: Double? = null,
    val classCode: String = "",
    val isin: String = "",
    val moneyPerPointOfPrice: Double? = null,
    val instrumentType: InstrumentType = InstrumentType.OTHER,
    // Для валютных позиций (instrumentType = "currency").
    // Из WS PositionsStream: money — свободный остаток,
    // blocked — заблокированный. Обе величины — в единицах
    // валюты (для rub — рубли, для других валют — единицы
    // этой валюты).
    // Формула связи money, blocked и quantity пока не
    // подтверждена по документации T-Invest. Возможные
    // варианты (quantity = money, quantity = money + blocked,
    // money и blocked — производные от quantity) проверяются
    // на реальных данных. Поэтому храним раздельно:
    // интерпретация откладывается до UI-задачи, и модель
    // не придётся менять, какая бы формула ни оказалась
    // верной.
    val money: Double? = null,
    val blocked: Double? = null,
    // Код валюты (ISO-4217, например "rub") для валютных
    // позиций (instrumentType = "currency"). Нужен для
    // склейки money/blocked из WS-стрима (там только
    // currency, без uid и ticker) с валютной позицией,
    // пришедшей из polling.
    val currency: String? = null,
    // Идентификатор счёта-источника события. Заполняется с момента
    // multi-account; null — для обратной совместимости в тестах.
    val accountId: String? = null,
    // Домашняя валюта брокера (`BrokerName.domesticCurrency`).
    // Валютная позиция с `isDomesticCurrency = true` — это деньги
    // в домашней валюте (RUB); в `Position` не попадает, идёт в
    // `AccountBalance`. `false` — иностранная валюта (`CurrencyPosition`).
    val isDomesticCurrency: Boolean = false,
)

/**
 * Доменная модель портфельной позиции для UI.
 *
 * Получается из [PositionStreamItem] через
 * [toPortfolioPosition] после обогащения в
 * `PositionFlowManager`. В отличие от [PositionStreamItem] —
 * всегда полная (все поля, которые нужны карточке).
 *
 * @property tscalpInstrumentId универсальный ключ инструмента.
 * @property brokerName брокер-источник.
 * @property name отображаемое имя (сейчас — `ticker`).
 * @property ticker биржевой тикер.
 * @property quantity количество (лоты/единицы).
 * @property currentPrice текущая цена (`0.0`, если от стрима
 *   пришло `null` до обогащения).
 * @property totalValue `currentPrice * quantity`.
 * @property instrumentType строковый тип: `"share"`,
 *   `"futures"`, `"currency"`, `"etf"`.
 * @property classCode код режима торгов.
 * @property isin ISIN инструмента.
 * @property averagePrice средняя цена позиции. `null` — нет
 *   данных (например, позиция только что открыта).
 * @property profit P&L в рублях. Источник: `expectedYield`
 *   от брокера (passthrough). `null` — сервер не прислал.
 *   Client-side fallback — отдельная задача
 *   `### P&L: client-side fallback для PortfolioPosition`.
 * @property profitPercent P&L в процентах от вложенных
 *   средств. Считается через [calculateProfitPercent] —
 *   учитывает `pointValue` для фьючерсов.
 * @property priceChangePercent изменение цены в процентах
 *   (может быть `null`).
 * @property moneyPerPointOfPrice стоимость шага цены (только фьючерсы).
 * @property currency ISO-4217 код валюты (для
 *   `instrumentType = "currency"`); нужен для склейки с WS.
 */
data class PortfolioPosition(
    // Обязательные: без них позицию нельзя корректно отрисовать
    val tscalpInstrumentId: String,
    val brokerName: BrokerName,
    val name: String,
    val ticker: String,
    val quantity: Long,
    val currentPrice: Double,
    val totalValue: Double,
    // Опциональные: с разумными дефолтами
    val instrumentType: InstrumentType = InstrumentType.OTHER,
    val classCode: String = "",
    val isin: String = "",
    val averagePrice: Double? = null,
    val profit: Double? = null,
    val profitPercent: Double? = null,
    val priceChangePercent: Double? = null,
    val moneyPerPointOfPrice: Double? = null,
    // Код валюты (ISO-4217) для валютных позиций.
    // Источник — Instrument.currency в fetchPortfolioPositions.
    // Используется для склейки с WS-стримом (money/blocked).
    val currency: String? = null,
    // Идентификатор счёта. Заполняется из Position.accountId в toUiPosition.
    // Нужен для группировки UI по AccountKey (мультисчёт).
    val accountId: String? = null,
    // Заблокированное количество бумаг. Заполняется из Position.lockedQuantity.
    // T-Invest blocked, BCS locked. `null` — нет данных.
    val lockedQuantity: Double? = null,
)

/**
 * Прибыль/убыток в процентах от вложенных средств.
 *
 * Формула: yield / (avgPrice × quantity × pointValue) × 100.
 *  - Для акций pointValue = 1 (или null → считаем 1.0),
 *    знаменатель — avgPrice × quantity в рублях.
 *  - Для фьючерсов avgPrice в пунктах, pointValue переводит
 *    вложение в рубли; серверный yield уже в рублях.
 *
 * Возвращает null, если данных недостаточно для расчёта.
 */
fun calculateProfitPercent(
    yield: Double?,
    avgPrice: Double?,
    quantity: Long,
    pointValue: Double?,
): Double? {
    if (yield == null) return null
    if (avgPrice == null || avgPrice <= 0.0) return null
    if (quantity <= 0L) return null
    val point = pointValue?.takeIf { it > 0.0 } ?: 1.0
    val invested = avgPrice * quantity * point
    if (invested <= 0.0) return null
    return yield / invested * 100.0
}

/**
 * Пустая [PortfolioPosition] для инструмента, которого нет в портфеле
 * (например, в карточке заявки, когда позиция ещё не открыта).
 *
 * Все производные presentation-поля (`profit`, `profitPercent`,
 * `totalValue`) вычисляются по тем же правилам, что и в
 * [toPortfolioPosition]: `totalValue = currentPrice * quantity`
 * (при `quantity = 0` → `0.0`), `profit`/`profitPercent` — `null`
 * (нет позиции — нет P&L).
 *
 * Фабрика в domain, а не в presentation: правила конструирования
 * domain-модели принадлежат domain (clean-audit Н1).
 */
fun emptyPortfolioPosition(
    instrument: Instrument,
    currentPrice: Double,
    moneyPerPointOfPrice: Double?,
    instrumentType: InstrumentType,
): PortfolioPosition =
    PortfolioPosition(
        brokerName = BrokerName.TINVEST,
        tscalpInstrumentId = instrument.tscalpInstrumentId,
        name = instrument.name,
        isin = instrument.isin,
        ticker = instrument.ticker,
        classCode = instrument.classCode ?: "",
        quantity = 0L,
        currentPrice = currentPrice,
        totalValue = currentPrice * 0L,
        profit = null,
        profitPercent = null,
        instrumentType = instrumentType,
        moneyPerPointOfPrice = moneyPerPointOfPrice,
    )

/**
 * SSoT-merge: применяет новое событие [PositionStreamItem] к существующей
 * [PortfolioPosition]. Канон (по образцу `PortfolioViewModel.updatePortfolioItem`):
 *
 * - `quantity` — из события (не объединяется).
 * - `currentPrice`, `averagePrice`, `pointValue` — non-null из события,
 *   иначе старое значение (partial-событие не обнуляет).
 * - `totalValue` = `currentPrice * quantity`.
 * - `profit` — `expectedYield` события (может быть `null`, если не пришло).
 * - `profitPercent` — через [calculateProfitPercent] с `newAvgPrice` и
 *   `newPointValue`.
 * - `instrumentType` — из события.
 * - `isin`, `classCode` — `ifBlank { old }` (пустое событие не обнуляет).
 *
 * Политика фильтрации partial-событий — в presentation-слое (VM).
 * Domain предоставляет механизм, presentation решает, применять ли.
 *
 * Clean-audit Н24.
 */
fun PortfolioPosition.mergeWith(item: PositionStreamItem): PortfolioPosition {
    val newPrice = item.currentPrice ?: currentPrice
    val newAvgPrice = item.averagePositionPrice ?: averagePrice
    val newMoneyPerPointOfPrice = item.moneyPerPointOfPrice ?: moneyPerPointOfPrice
    return copy(
        quantity = item.quantity,
        currentPrice = newPrice,
        averagePrice = newAvgPrice,
        totalValue = newPrice * item.quantity,
        profit = item.expectedYield,
        profitPercent =
            calculateProfitPercent(
                yield = item.expectedYield,
                avgPrice = newAvgPrice,
                quantity = item.quantity,
                pointValue = newMoneyPerPointOfPrice,
            ),
        moneyPerPointOfPrice = newMoneyPerPointOfPrice,
        instrumentType = item.instrumentType,
        isin = item.isin.ifBlank { isin },
        classCode = item.classCode.ifBlank { classCode },
    )
}

/**
 * Преобразует элемент потока позиций в доменную модель портфельной позиции.
 * profitPercent считается универсально — см. calculateProfitPercent.
 */
fun PositionStreamItem.toPortfolioPosition(): PortfolioPosition {
    val price = currentPrice ?: 0.0
    return PortfolioPosition(
        tscalpInstrumentId = this.tscalpInstrumentId,
        brokerName = brokerName,
        name = ticker,
        ticker = ticker,
        quantity = quantity,
        currentPrice = price,
        averagePrice = averagePositionPrice,
        totalValue = price * quantity,
        profit = expectedYield,
        profitPercent =
            calculateProfitPercent(
                yield = expectedYield,
                avgPrice = averagePositionPrice,
                quantity = quantity,
                pointValue = moneyPerPointOfPrice,
            ),
        moneyPerPointOfPrice = moneyPerPointOfPrice,
        instrumentType = instrumentType,
        isin = isin,
        classCode = classCode,
    )
}

// =============================================================================
// Portfolio model (multi-account). See docs/domain_models.md §Portfolio.
// =============================================================================

/**
 * Позиция по инструменту в конкретном счёте. Один счёт — одна позиция
 * на инструмент. Мультиброкерность и мультисчётность — обязательны.
 *
 * Тип инструмента — композиция: `position.instrument: Instrument`.
 * Subtype `Position` соответствует subtype `Instrument` — компилятор
 * проверяет соответствие (`when (position)` exhaustive по 7 подтипам).
 *
 * `moneyPerPointOfPrice` — extension из `Instrument` (см. `Instruments.kt`).
 * Для акций/облигаций/ETF — 1.0; для фьючерсов — реальный множитель.
 *
 * @property instrument инструмент позиции.
 * @property brokerName брокер-источник.
 * @property accountId идентификатор счёта.
 * @property quantity количество (баланс, лоты/единицы). `Double`:
 *   Finam поддерживает дробное, T-Invest/BCS — целое.
 * @property currentPrice текущая цена.
 * @property averagePrice средняя цена позиции. `null` — нет данных.
 * @property profit P&L в рублях от брокера. `null` — брокер не прислал.
 * @property lockedQuantity заблокированное количество бумаг.
 *   T-Invest `blocked`, BCS `locked`.
 * @property priceChangePercent изменение цены за торговый день в процентах
 *   (от цены открытия дня). Источник: DTO брокера (`dailyYieldRelative`
 *   у T-Invest, `dailyPercentPL` у BCS, `daily_pnl` у Finam). `null` — нет данных.
 */
sealed interface Position {
    val instrument: Instrument
    val brokerName: BrokerName
    val accountId: String
    val quantity: Double
    val currentPrice: Double
    val averagePrice: Double?
    val profit: Double?
    val lockedQuantity: Double?
    val priceChangePercent: Double?

    /** Стоимость позиции в рублях. */
    val totalValue: Double
        get() = currentPrice * quantity * instrument.moneyPerPointOfPrice

    /** P&L в процентах от вложенных средств (от средней цены). */
    val profitPercent: Double?
        get() {
            val p = profit ?: return null
            val avg = averagePrice?.takeIf { it > 0.0 } ?: return null
            if (quantity <= 0.0) return null
            val invested = avg * quantity * instrument.moneyPerPointOfPrice
            if (invested <= 0.0) return null
            return p / invested * 100.0
        }
}

/** Позиция по акции. */
data class SharePosition(
    override val instrument: Share,
    override val brokerName: BrokerName,
    override val accountId: String,
    override val quantity: Double,
    override val currentPrice: Double,
    override val averagePrice: Double? = null,
    override val profit: Double? = null,
    override val lockedQuantity: Double? = null,
    override val priceChangePercent: Double? = null,
) : Position

/** Позиция по фьючерсу. */
data class FuturePosition(
    override val instrument: Future,
    override val brokerName: BrokerName,
    override val accountId: String,
    override val quantity: Double,
    override val currentPrice: Double,
    override val averagePrice: Double? = null,
    override val profit: Double? = null,
    override val lockedQuantity: Double? = null,
    override val priceChangePercent: Double? = null,
) : Position

/** Позиция по облигации. */
data class BondPosition(
    override val instrument: Bond,
    override val brokerName: BrokerName,
    override val accountId: String,
    override val quantity: Double,
    override val currentPrice: Double,
    override val averagePrice: Double? = null,
    override val profit: Double? = null,
    override val lockedQuantity: Double? = null,
    override val priceChangePercent: Double? = null,
) : Position

/** Позиция по опциону. */
data class OptionPosition(
    override val instrument: Option,
    override val brokerName: BrokerName,
    override val accountId: String,
    override val quantity: Double,
    override val currentPrice: Double,
    override val averagePrice: Double? = null,
    override val profit: Double? = null,
    override val lockedQuantity: Double? = null,
    override val priceChangePercent: Double? = null,
) : Position

/** Позиция по ETF. */
data class EtfPosition(
    override val instrument: Etf,
    override val brokerName: BrokerName,
    override val accountId: String,
    override val quantity: Double,
    override val currentPrice: Double,
    override val averagePrice: Double? = null,
    override val profit: Double? = null,
    override val lockedQuantity: Double? = null,
    override val priceChangePercent: Double? = null,
) : Position

/** Позиция по валюте иностранного государства. Деньги RUB — в `AccountBalance`, не здесь. */
data class CurrencyPosition(
    override val instrument: Currency,
    override val brokerName: BrokerName,
    override val accountId: String,
    override val quantity: Double,
    override val currentPrice: Double,
    override val averagePrice: Double? = null,
    override val profit: Double? = null,
    override val lockedQuantity: Double? = null,
    override val priceChangePercent: Double? = null,
) : Position

/** Позиция по прочему инструменту. */
data class OtherPosition(
    override val instrument: Other,
    override val brokerName: BrokerName,
    override val accountId: String,
    override val quantity: Double,
    override val currentPrice: Double,
    override val averagePrice: Double? = null,
    override val profit: Double? = null,
    override val lockedQuantity: Double? = null,
    override val priceChangePercent: Double? = null,
) : Position

/**
 * Денежный остаток по валюте в конкретном счёте.
 * Не позиция (валюта — не ценная бумага).
 *
 * @property currency ISO-4217 код валюты (`"rub"`).
 * @property money свободный остаток.
 * @property blocked заблокированный остаток.
 */
data class AccountBalance(
    val currency: String,
    val money: Double,
    val blocked: Double,
) {
    /** Суммарный остаток по валюте. */
    val total: Double get() = money + blocked
}

/**
 * Агрегат одного счёта: позиции + денежные остатки по валютам.
 *
 * @property accountId идентификатор счёта.
 * @property brokerName брокер счёта.
 * @property positions позиции счёта.
 * @property balances денежные остатки по валютам.
 */
data class AccountPortfolio(
    val accountId: String,
    val brokerName: BrokerName,
    val positions: List<Position>,
    val balances: List<AccountBalance>,
) {
    /** Суммарный P&L по всем позициям. `null` — если ни у одной нет profit. */
    val totalProfit: Double?
        get() {
            val withProfit = positions.mapNotNull { it.profit }
            if (withProfit.isEmpty()) return null
            return withProfit.sum()
        }
}

/**
 * Мультисчётный агрегат: `Map<AccountKey, AccountPortfolio>`.
 *
 * Единственный публичный эмитируемый тип `PositionFlowManager`.
 * Порядок: `BrokerName.entries` (TINVEST, BCS, FINAM), внутри — `accountId`
 * лексикографически. Детерминированно.
 *
 * См. `docs/domain_models.md` §4.x.
 */
data class PortfolioSheet(
    val accounts: Map<AccountKey, AccountPortfolio>,
)

/**
 * Составной ключ счёта: `(brokerName, accountId)`.
 *
 * Единственный идентификатор счёта в `PortfolioSheet`.
 */
data class AccountKey(
    val brokerName: BrokerName,
    val accountId: String,
)

// =============================================================================
// Converters: PositionStreamItem -> Position / AccountBalance.
// See docs/domain_models.md §Portfolio.
// =============================================================================

/**
 * Преобразует [PositionStreamItem] в [Position] с указанным [instrument].
 *
 * Возвращает `null`, если конверсия невозможна (вызывающий может залогировать):
 * - `instrument == null` (инструмент не резолвнут).
 * - `accountId == null` (не установлен контекст счёта).
 * - `isDomesticCurrency = true` и `instrument` — это `Currency`: домашняя
 *   валюта (RUB) — это деньги, её место в `AccountBalance`, а не в `Position`.
 *   См. [toAccountBalance].
 * - Mismatch: `item.instrumentType` не совпадает с `instrument.instrumentType`
 *   и не равен `OTHER` (OTHER = «не задано, доверяем instrument»).
 *
 * Subtype `Position` определяется по типу [instrument]:
 * `Share` -> [SharePosition], `Future` -> [FuturePosition] и т.д.
 * `lockedQuantity` и `priceChangePercent` пока `null` — источники в
 * задаче `Position: источники priceChangePercent и lockedQuantity`.
 */
fun PositionStreamItem.toPosition(instrument: Instrument?): Position? {
    if (instrument == null) return null
    val acc = accountId ?: return null
    if (isDomesticCurrency && instrument is Currency) return null
    if (instrumentType != instrument.instrumentType && instrumentType != InstrumentType.OTHER) return null
    val c = PositionCommon.of(this, acc)
    return when (instrument) {
        is Share -> SharePosition(instrument, c.brokerName, c.accountId, c.quantity, c.currentPrice, c.averagePrice, c.profit)
        is Future -> FuturePosition(instrument, c.brokerName, c.accountId, c.quantity, c.currentPrice, c.averagePrice, c.profit)
        is Bond -> BondPosition(instrument, c.brokerName, c.accountId, c.quantity, c.currentPrice, c.averagePrice, c.profit)
        is Option -> OptionPosition(instrument, c.brokerName, c.accountId, c.quantity, c.currentPrice, c.averagePrice, c.profit)
        is Etf -> EtfPosition(instrument, c.brokerName, c.accountId, c.quantity, c.currentPrice, c.averagePrice, c.profit)
        is Currency -> CurrencyPosition(instrument, c.brokerName, c.accountId, c.quantity, c.currentPrice, c.averagePrice, c.profit)
        is Other -> OtherPosition(instrument, c.brokerName, c.accountId, c.quantity, c.currentPrice, c.averagePrice, c.profit)
    }
}

/** Общие поля конструктора [Position] (из [PositionStreamItem] + резолвнутый accountId). */
private class PositionCommon(
    val brokerName: BrokerName,
    val accountId: String,
    val quantity: Double,
    val currentPrice: Double,
    val averagePrice: Double?,
    val profit: Double?,
) {
    companion object {
        fun of(
            item: PositionStreamItem,
            accountId: String,
        ): PositionCommon =
            PositionCommon(
                brokerName = item.brokerName,
                accountId = accountId,
                quantity = item.quantity.toDouble(),
                currentPrice = item.currentPrice ?: 0.0,
                averagePrice = item.averagePositionPrice,
                profit = item.expectedYield,
            )
    }
}

/**
 * Преобразует [PositionStreamItem] в [AccountBalance].
 *
 * Возвращает `null`, если:
 * - `instrumentType != CURRENCY` (не валютная позиция).
 * - `isDomesticCurrency = false` (иностранная валюта — это [CurrencyPosition],
 *   см. [toPosition]).
 *
 * `currency == null` — fallback на `brokerName.domesticCurrency` (брокер знает
 * домашнюю валюту; для `isDomesticCurrency = true` это корректно).
 * `money`/`blocked` — `0.0` при `null` (нет данных — пустой баланс).
 */
fun PositionStreamItem.toAccountBalance(): AccountBalance? {
    if (instrumentType != InstrumentType.CURRENCY) return null
    if (!isDomesticCurrency) return null
    val cur = currency ?: brokerName.domesticCurrency
    return AccountBalance(
        currency = cur,
        money = money ?: 0.0,
        blocked = blocked ?: 0.0,
    )
}

/**
 * Обратный адаптер [Position] -> [PositionStreamItem].
 *
 * Нужен для `TInvestOrchestrator.positionsSnapshot`: тот возвращает
 * `List<PositionStreamItem>` для merge в `PositionFlowManager`, а источник
 * (`PortfolioApi.fetchPortfolioPositions`) уже отдаёт `AccountPortfolio`.
 *
 * `quantity` приводится к `Long` (lossy — Finam дробное; до миграции Finam
 * не работает). `isDomesticCurrency = false` — `Position` для currency только
 * иностранная (RUB идёт в `AccountBalance`). `money`/`blocked` = `null`
 * (их место — `AccountBalance`).
 */
internal fun Position.toStreamItem(): PositionStreamItem =
    PositionStreamItem(
        tscalpInstrumentId = instrument.tscalpInstrumentId,
        brokerName = brokerName,
        ticker = instrument.ticker,
        quantity = quantity.toLong(),
        currentPrice = currentPrice,
        averagePositionPrice = averagePrice,
        expectedYield = profit,
        classCode = instrument.classCode ?: "",
        isin = instrument.isin,
        moneyPerPointOfPrice = instrument.moneyPerPointOfPrice,
        instrumentType = instrument.instrumentType,
        accountId = accountId,
        isDomesticCurrency = false,
    )
