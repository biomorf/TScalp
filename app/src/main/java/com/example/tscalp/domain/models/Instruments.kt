// app/src/main/java/.../domain/models/Instruments.kt

package com.example.tscalp.domain.models

import java.time.Instant

/**
 * Базовый класс торгового инструмента.
 * Содержит поля, общие для всех типов, а также поля,
 * специфичные для отдельных типов (акции, фьючерсы, опционы и т.д.).
 * * Остаётся открытым для наследования.
 * Поля, не применимые к конкретному типу, остаются null.
 */
open class InstrumentUi(
    open val tscalpInstrumentId: String,    // универсальный идентификатор TScalp
    open val ticker: String,
    open val classCode: String,            // код класса (например, SPBFUT)
    open val isin: String,                 // ISIN
    open val ttech_uid: String,                  // instrument_uid из T-tech Invest API
    open val ttech_figi: String = "",   // временно для стрима LastPrice
    open val name: String,
    open val currency: String,
    open val lot: Int,
    open val instrumentType: String = "",   /// тип инструмента: share, bond, etf, currency, futures, option

    // --- Общие поля (присутствуют у большинства типов) ---
    open val exchange: String? = null,                // торговая площадка
    open val tradingStatus: String? = null,          // режим торгов
    open val apiTradeAvailableFlag: Boolean? = null, // доступность через API
    open val buyAvailableFlag: Boolean? = null,
    open val sellAvailableFlag: Boolean? = null,
    open val shortEnabledFlag: Boolean? = null,
    open val minPriceIncrement: Double? = null,      // шаг цены
    open val minPriceIncrementAmount: Double? = null,// стоимость шага цены
    open val klong: Double? = null,                  // коэффициент риска long КСУР
    open val kshort: Double? = null,
    open val dlong: Double? = null,                  // ставка начальной маржи long КСУР
    open val dshort: Double? = null,
    open val dlongMin: Double? = null,               // ставка начальной маржи long КПУР
    open val dshortMin: Double? = null,
    open val first1minCandleDate: java.time.Instant? = null,
    open val first1dayCandleDate: java.time.Instant? = null,
    open val forIisFlag: Boolean? = null,
    open val forQualInvestorFlag: Boolean? = null,
    open val weekendFlag: Boolean? = null,
    open val blockedTcaFlag: Boolean? = null,
    open val countryOfRisk: String? = null,
    open val countryOfRiskName: String? = null,
    open val sector: String? = null,
    open val brand: String? = null,                   // (упрощённо)
    open val requiredTests: List<String>? = null,

    // --- Поля, специфичные для акций (Share) ---
    open val ipoDate: java.time.Instant? = null,
    open val issueSize: Long? = null,
    open val issueSizePlan: Long? = null,
    open val nominal: Double? = null,
    open val divYieldFlag: Boolean? = null,
    open val shareType: String? = null,
    open val liquidityFlag: Boolean? = null,
    open val assetUid: String? = null,
    open val instrumentExchange: String? = null,

    // --- Поля, специфичные для фьючерсов (Future) ---
    open val expirationDate: java.time.Instant? = null,
    open val firstTradeDate: java.time.Instant? = null,
    open val lastTradeDate: java.time.Instant? = null,
    open val futuresType: String? = null,
    open val assetType: String? = null,
    open val basicAsset: String? = null,
    open val basicAssetSize: Double? = null,
    open val positionUid: String? = null,
    open val basicAssetPositionUid: String? = null,
    open val initialMarginOnBuy: Double? = null,
    open val initialMarginOnSell: Double? = null,
    open val dlongClient: Double? = null,
    open val dshortClient: Double? = null
)

/**
 * Модель фьючерса.
 * Содержит все поля базового класса, а также дополнительные поля,
 * специфичные для фьючерсов, и вычисляемое свойство pointValue.
 */
data class FutureUi(
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val classCode: String,
    override val isin: String,
    override val ttech_uid: String,
    override val ttech_figi: String = "",
    override val name: String,
    override val currency: String,
    override val lot: Int,
    override val instrumentType: String = "futures",

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
    override val first1minCandleDate: java.time.Instant? = null,
    override val first1dayCandleDate: java.time.Instant? = null,
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
    override val expirationDate: java.time.Instant? = null,
    override val firstTradeDate: java.time.Instant? = null,
    override val lastTradeDate: java.time.Instant? = null,
    override val futuresType: String? = null,
    override val assetType: String? = null,
    override val basicAsset: String? = null,
    override val basicAssetSize: Double? = null,
    override val positionUid: String? = null,
    override val basicAssetPositionUid: String? = null,
    override val initialMarginOnBuy: Double? = null,
    override val initialMarginOnSell: Double? = null,
    override val dlongClient: Double? = null,
    override val dshortClient: Double? = null
) : InstrumentUi(
    tscalpInstrumentId, ticker, classCode, isin, ttech_uid, ttech_figi, name, currency, lot, instrumentType,
    exchange, tradingStatus, apiTradeAvailableFlag, buyAvailableFlag, sellAvailableFlag, shortEnabledFlag,
    minPriceIncrement, minPriceIncrementAmount, klong, kshort, dlong, dshort, dlongMin, dshortMin,
    first1minCandleDate, first1dayCandleDate, forIisFlag, forQualInvestorFlag, weekendFlag, blockedTcaFlag,
    countryOfRisk, countryOfRiskName, sector, brand, requiredTests,
    ipoDate = null, issueSize = null, issueSizePlan = null, nominal = null, divYieldFlag = null,
    shareType = null, liquidityFlag = null, assetUid = null, instrumentExchange = null,
    expirationDate, firstTradeDate, lastTradeDate, futuresType, assetType, basicAsset, basicAssetSize,
    positionUid, basicAssetPositionUid, initialMarginOnBuy, initialMarginOnSell, dlongClient, dshortClient
) {
    /** Стоимость одного пункта цены в рублях (для фьючерсов) */
    val pointValue: Double
        get() {
            val inc = minPriceIncrement ?: 0.0
            val amount = minPriceIncrementAmount ?: 0.0
            return if (inc > 0.0) amount / inc else 1.0
        }
}

/**
 * Модель акции.
 * Содержит все поля базового класса, а также дополнительные поля,
 * специфичные для акций.
 */
data class ShareUi(
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val classCode: String,
    override val isin: String,
    override val ttech_uid: String,
    override val ttech_figi: String = "",
    override val name: String,
    override val currency: String,
    override val lot: Int,
    override val instrumentType: String = "share",

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
    override val first1minCandleDate: java.time.Instant? = null,
    override val first1dayCandleDate: java.time.Instant? = null,
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
    override val ipoDate: java.time.Instant? = null,
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
    tscalpInstrumentId, ticker, classCode, isin, ttech_uid, ttech_figi, name, currency, lot, instrumentType,
    exchange, tradingStatus, apiTradeAvailableFlag, buyAvailableFlag, sellAvailableFlag, shortEnabledFlag,
    minPriceIncrement, minPriceIncrementAmount, klong, kshort, dlong, dshort, dlongMin, dshortMin,
    first1minCandleDate, first1dayCandleDate, forIisFlag, forQualInvestorFlag, weekendFlag, blockedTcaFlag,
    countryOfRisk, countryOfRiskName, sector, brand, requiredTests,
    ipoDate, issueSize, issueSizePlan, nominal, divYieldFlag, shareType, liquidityFlag, assetUid, instrumentExchange,
    // фьючерсные поля – null
    expirationDate = null, firstTradeDate = null, lastTradeDate = null,
    futuresType = null, assetType = null, basicAsset = null, basicAssetSize = null,
    positionUid = null, basicAssetPositionUid = null, initialMarginOnBuy = null,
    initialMarginOnSell = null, dlongClient = null, dshortClient = null
)