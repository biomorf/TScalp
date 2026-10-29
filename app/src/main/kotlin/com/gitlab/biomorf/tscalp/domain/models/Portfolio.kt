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
 * @property pointValue стоимость шага цены (только фьючерсы).
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
    val pointValue: Double? = null,
    val instrumentType: String = "",
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
 * @property pointValue стоимость шага цены (только фьючерсы).
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
    val instrumentType: String = "",
    val classCode: String = "",
    val isin: String = "",
    val averagePrice: Double? = null,
    val profit: Double? = null,
    val profitPercent: Double? = null,
    val priceChangePercent: Double? = null,
    val pointValue: Double? = null,
    // Код валюты (ISO-4217) для валютных позиций.
    // Источник — InstrumentUi.currency в fetchPortfolioPositions.
    // Используется для склейки с WS-стримом (money/blocked).
    val currency: String? = null,
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
    instrument: InstrumentUi,
    currentPrice: Double,
    pointValue: Double?,
    instrumentType: String,
): PortfolioPosition =
    PortfolioPosition(
        brokerName = BrokerName.TINVEST,
        tscalpInstrumentId = instrument.tscalpInstrumentId,
        name = instrument.name,
        isin = instrument.isin,
        ticker = instrument.ticker,
        classCode = instrument.classCode,
        quantity = 0L,
        currentPrice = currentPrice,
        totalValue = currentPrice * 0L,
        profit = null,
        profitPercent = null,
        instrumentType = instrumentType,
        pointValue = pointValue,
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
    val newPointValue = item.pointValue ?: pointValue
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
                pointValue = newPointValue,
            ),
        pointValue = newPointValue,
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
                pointValue = pointValue,
            ),
        pointValue = pointValue,
        instrumentType = instrumentType,
        isin = isin,
        classCode = classCode,
    )
}
