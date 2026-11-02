// app/src/main/java/.../domain/models/Instruments.kt

package com.gitlab.biomorf.tscalp.domain.models

import kotlin.time.Instant

/**
 * Базовый класс торгового инструмента.
 *
 * Даты — `kotlin.time.Instant` (KMP-safe, stdlib с Kotlin 2.3+).
 *
 * Содержит поля, общие для всех типов, а также поля,
 * специфичные для отдельных типов (акции, фьючерсы, опционы и т.д.).
 * Остаётся открытым для наследования: [FutureUi], [ShareUi] —
 * подтипы с переопределёнными полями. Поля, не применимые к
 * конкретному типу, остаются `null`.
 *
 * @property tscalpInstrumentId универсальный идентификатор
 *   инструмента в TScalp (не `uid`/`figi`). Domain-слой оперирует
 *   только им; трансляция в брокерские id — в data-слое.
 * @property figi FIGI инструмента (международный идентификатор,
 *   Bloomberg). Не T-Invest-специфичный: BCS в позициях тоже
 *   даёт figi.
 * @property instrumentType строковый enum: `"share"`, `"futures"`,
 *   `"currency"`, `"etf"`, `"bond"`, `"option"`. Не sealed-тип,
 *   потому что часть моделей использует один общий класс.
 */

/**
 * Тип инструмента (универсальный для всех брокеров).
 *
 * Заполняется маппером по контексту (T-Invest: `Shares()` → SHARE,
 * `Futures()` → FUTURES) или `fromBrokerString` (REST/WS-строки).
 * C-min шаг 10 (§7 `docs/domain_models.md`).
 */
enum class InstrumentType {
    SHARE,
    FOREIGN_SHARE,
    BOND,
    NOTE,
    EURO_BOND,
    DEPOSITARY_RECEIPT,
    ETF,
    MUTUAL_FUND,
    FUTURES,
    OPTION,
    CURRENCY,
    COMMODITY,
    INDEX,
    OTHER,
    ;

    companion object {
        /**
         * Broker string → [InstrumentType]. Case-insensitive.
         * T-Invest (lower): share, bond, etf, currency, futures, option, fond.
         * BCS (UPPER): STOCK, FOREIGN_STOCK, BONDS, NOTES, ...
         * Finam (UPPER): STOCK, BOND, FUTURES, ...
         */
        fun fromBrokerString(value: String): InstrumentType =
            when (value.lowercase()) {
                "share", "stock" -> SHARE
                "foreign_stock" -> FOREIGN_SHARE
                "bond", "bonds" -> BOND
                "note", "notes" -> NOTE
                "euro_bond", "euro_bonds" -> EURO_BOND
                "depositary_receipt", "depositary_receipts" -> DEPOSITARY_RECEIPT
                "etf" -> ETF
                "mutual_fund", "mutual_funds", "fond" -> MUTUAL_FUND
                "futures" -> FUTURES
                "option", "options" -> OPTION
                "currency" -> CURRENCY
                "commodity", "goods" -> COMMODITY
                "index", "indices" -> INDEX
                else -> OTHER
            }
    }
}

open class InstrumentUi(
    open val tscalpInstrumentId: String, // универсальный идентификатор TScalp
    open val ticker: String,
    open val classCode: String? = null, // код класса (например, SPBFUT)
    open val isin: String, // ISIN
    open val figi: String? = null,
    open val name: String,
    open val currency: String? = null,
    open val lot: Long? = null,
    open val instrumentType: InstrumentType = InstrumentType.OTHER,
    // --- Общие поля (присутствуют у большинства типов) ---
    open val exchange: String? = null, // торговая площадка
    open val tradingStatus: String? = null, // режим торгов
    open val apiTradeAvailableFlag: Boolean? = null, // доступность через API
    open val buyAvailableFlag: Boolean? = null,
    open val sellAvailableFlag: Boolean? = null,
    open val shortEnabledFlag: Boolean? = null,
    open val minPriceIncrement: Double? = null, // шаг цены
    open val minPriceIncrementAmount: Double? = null, // стоимость шага цены
    open val klong: Double? = null, // коэффициент риска long КСУР
    open val kshort: Double? = null,
    open val dlong: Double? = null, // ставка начальной маржи long КСУР
    open val dshort: Double? = null,
    open val dlongMin: Double? = null, // ставка начальной маржи long КПУР
    open val dshortMin: Double? = null,
    open val first1minCandleDate: Instant? = null,
    open val first1dayCandleDate: Instant? = null,
    open val forIisFlag: Boolean? = null,
    open val forQualInvestorFlag: Boolean? = null,
    open val weekendFlag: Boolean? = null,
    open val blockedTcaFlag: Boolean? = null,
    open val countryOfRisk: String? = null,
    open val countryOfRiskName: String? = null,
    open val sector: String? = null,
    open val brand: String? = null, // (упрощённо)
    open val requiredTests: List<String>? = null,
    // --- Поля, специфичные для акций (Share) ---
    open val ipoDate: Instant? = null,
    open val issueSize: Long? = null,
    open val issueSizePlan: Long? = null,
    open val nominal: Double? = null,
    open val divYieldFlag: Boolean? = null,
    open val shareType: String? = null,
    open val liquidityFlag: Boolean? = null,
    open val assetUid: String? = null,
    open val instrumentExchange: String? = null,
    // --- Поля, специфичные для фьючерсов (Future) ---
    open val expirationDate: Instant? = null,
    open val firstTradeDate: Instant? = null,
    open val lastTradeDate: Instant? = null,
    open val futuresType: String? = null,
    open val assetType: String? = null,
    open val basicAsset: String? = null,
    open val basicAssetSize: Double? = null,
    open val positionUid: String? = null,
    open val basicAssetPositionUid: String? = null,
    open val initialMarginOnBuy: Double? = null,
    open val initialMarginOnSell: Double? = null,
    open val dlongClient: Double? = null,
    open val dshortClient: Double? = null,
    open val settlementCurrency: String? = null, // валюта расчётов (Option/bond_details), не заполняется
)

/**
 * Модель фьючерса.
 * Содержит все поля базового класса, а также дополнительные поля,
 * специфичные для фьючерсов, и вычисляемое свойство pointValue.
 */
data class FutureUi(
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val classCode: String? = null,
    override val isin: String,
    override val figi: String? = null,
    override val name: String,
    override val currency: String? = null,
    override val lot: Long? = null,
    override val instrumentType: InstrumentType = InstrumentType.FUTURES,
    // Поля, специфичные для фьючерсов, с переопределением из базового класса
    override val exchange: String? = null,
    override val tradingStatus: String? = null,
    override val apiTradeAvailableFlag: Boolean? = null,
    override val buyAvailableFlag: Boolean? = null,
    override val sellAvailableFlag: Boolean? = null,
    override val shortEnabledFlag: Boolean? = null,
    override val minPriceIncrement: Double? = null,
    override val minPriceIncrementAmount: Double? = null,
    override val klong: Double? = null,
    override val kshort: Double? = null,
    override val dlong: Double? = null,
    override val dshort: Double? = null,
    override val dlongMin: Double? = null,
    override val dshortMin: Double? = null,
    override val first1minCandleDate: Instant? = null,
    override val first1dayCandleDate: Instant? = null,
    override val forIisFlag: Boolean? = null,
    override val forQualInvestorFlag: Boolean? = null,
    override val weekendFlag: Boolean? = null,
    override val blockedTcaFlag: Boolean? = null,
    override val countryOfRisk: String? = null,
    override val countryOfRiskName: String? = null,
    override val sector: String? = null,
    override val brand: String? = null,
    override val requiredTests: List<String>? = null,
    // Уникальные поля фьючерсов
    expirationDate: Instant? = null,
    firstTradeDate: Instant? = null,
    lastTradeDate: Instant? = null,
    override val futuresType: String? = null,
    override val assetType: String? = null,
    override val basicAsset: String? = null,
    override val basicAssetSize: Double? = null,
    override val positionUid: String? = null,
    override val basicAssetPositionUid: String? = null,
    override val initialMarginOnBuy: Double? = null,
    override val initialMarginOnSell: Double? = null,
    override val dlongClient: Double? = null,
    override val dshortClient: Double? = null,
) : InstrumentUi(
        tscalpInstrumentId,
        ticker,
        classCode,
        isin,
        figi,
        name,
        currency,
        lot,
        instrumentType,
        exchange,
        tradingStatus,
        apiTradeAvailableFlag,
        buyAvailableFlag,
        sellAvailableFlag,
        shortEnabledFlag,
        minPriceIncrement,
        minPriceIncrementAmount,
        klong,
        kshort,
        dlong,
        dshort,
        dlongMin,
        dshortMin,
        first1minCandleDate,
        first1dayCandleDate,
        forIisFlag,
        forQualInvestorFlag,
        weekendFlag,
        blockedTcaFlag,
        countryOfRisk,
        countryOfRiskName,
        sector,
        brand,
        requiredTests,
        ipoDate = null,
        issueSize = null,
        issueSizePlan = null,
        nominal = null,
        divYieldFlag = null,
        shareType = null,
        liquidityFlag = null,
        assetUid = null,
        instrumentExchange = null,
        expirationDate,
        firstTradeDate,
        lastTradeDate,
        futuresType,
        assetType,
        basicAsset,
        basicAssetSize,
        positionUid,
        basicAssetPositionUid,
        initialMarginOnBuy,
        initialMarginOnSell,
        dlongClient,
        dshortClient,
    )

/**
 * Модель акции.
 * Содержит все поля базового класса, а также дополнительные поля,
 * специфичные для акций.
 */
data class ShareUi(
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val classCode: String? = null,
    override val isin: String,
    override val figi: String? = null,
    override val name: String,
    override val currency: String? = null,
    override val lot: Long? = null,
    override val instrumentType: InstrumentType = InstrumentType.SHARE,
    // Общие поля (могут переопределяться)
    override val exchange: String? = null,
    override val tradingStatus: String? = null,
    override val apiTradeAvailableFlag: Boolean? = null,
    override val buyAvailableFlag: Boolean? = null,
    override val sellAvailableFlag: Boolean? = null,
    override val shortEnabledFlag: Boolean? = null,
    override val minPriceIncrement: Double? = null,
    override val minPriceIncrementAmount: Double? = null,
    override val klong: Double? = null,
    override val kshort: Double? = null,
    override val dlong: Double? = null,
    override val dshort: Double? = null,
    override val dlongMin: Double? = null,
    override val dshortMin: Double? = null,
    override val first1minCandleDate: Instant? = null,
    override val first1dayCandleDate: Instant? = null,
    override val forIisFlag: Boolean? = null,
    override val forQualInvestorFlag: Boolean? = null,
    override val weekendFlag: Boolean? = null,
    override val blockedTcaFlag: Boolean? = null,
    override val countryOfRisk: String? = null,
    override val countryOfRiskName: String? = null,
    override val sector: String? = null,
    override val brand: String? = null,
    override val requiredTests: List<String>? = null,
    // Уникальные поля акций
    override val ipoDate: Instant? = null,
    override val issueSize: Long? = null,
    override val issueSizePlan: Long? = null,
    override val nominal: Double? = null,
    override val divYieldFlag: Boolean? = null,
    override val shareType: String? = null,
    override val liquidityFlag: Boolean? = null,
    override val assetUid: String? = null,
    override val instrumentExchange: String? = null,
    // Поля фьючерсов скрыты (оставлены null)
    // ...
) : InstrumentUi(
        tscalpInstrumentId,
        ticker,
        classCode,
        isin,
        figi,
        name,
        currency,
        lot,
        instrumentType,
        exchange,
        tradingStatus,
        apiTradeAvailableFlag,
        buyAvailableFlag,
        sellAvailableFlag,
        shortEnabledFlag,
        minPriceIncrement,
        minPriceIncrementAmount,
        klong,
        kshort,
        dlong,
        dshort,
        dlongMin,
        dshortMin,
        first1minCandleDate,
        first1dayCandleDate,
        forIisFlag,
        forQualInvestorFlag,
        weekendFlag,
        blockedTcaFlag,
        countryOfRisk,
        countryOfRiskName,
        sector,
        brand,
        requiredTests,
        ipoDate,
        issueSize,
        issueSizePlan,
        nominal,
        divYieldFlag,
        shareType,
        liquidityFlag,
        assetUid,
        instrumentExchange,
        // фьючерсные поля – null
        expirationDate = null,
        firstTradeDate = null,
        lastTradeDate = null,
        futuresType = null,
        assetType = null,
        basicAsset = null,
        basicAssetSize = null,
        positionUid = null,
        basicAssetPositionUid = null,
        initialMarginOnBuy = null,
        initialMarginOnSell = null,
        dlongClient = null,
        dshortClient = null,
    )

/**
 * Стоимость одного пункта цены в рублях.
 *
 * Единая формула для всех типов инструментов:
 * `minPriceIncrementAmount / minPriceIncrement`.
 * Для акций / облигаций / ETF — `1.0` (`minPriceIncrementAmount = null`).
 * Для фьючерсов — реальный множитель (`pointValue`).
 *
 * Заменяет `FutureUi.pointValue` (C-min шаг 7): extension работает
 * на любом `InstrumentUi`, включая будущие подтипы.
 */
val InstrumentUi.moneyPerPointOfPrice: Double
    get() {
        val inc = minPriceIncrement ?: 0.0
        val amount = minPriceIncrementAmount ?: 0.0
        return if (inc > 0.0) amount / inc else 1.0
    }
