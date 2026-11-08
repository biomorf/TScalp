// app/src/main/java/.../domain/models/Instruments.kt

package com.gitlab.biomorf.tscalp.domain.models

import kotlin.time.Instant

/**
 * Доменная модель торгового инструмента (Phase 2, sealed interface).
 *
 * ISP/LSP: общие члены (core 4 + common 10 + 8 флагов = 22) — на интерфейсе.
 * Subtype-specific поля — только в своих data class (см. `docs/domain_models.md` §4/§5).
 * `when (instrument)` — exhaustive по подтипам.
 *
 * @property tscalpInstrumentId универсальный идентификатор TScalp.
 * @property ticker биржевой тикер.
 * @property name отображаемое имя.
 * @property instrumentType тип инструмента (enum).
 * @property classCode код класса (TQBR, SPBFUT, ...).
 * @property isin ISIN.
 * @property figi FIGI (международный идентификатор).
 * @property exchange торговая площадка.
 * @property lot размер лота.
 * @property currency валюта цены.
 * @property settlementCurrency валюта расчётов.
 * @property priceStepCost стоимость шага цены (бывш. minPriceIncrementAmount).
 * @property minPriceIncrement шаг цены.
 * @property expirationDate дата экспирации (Future / Option).
 * @property apiTradeAvailableFlag флаг доступности через API.
 * @property buyAvailableFlag флаг доступности покупки.
 * @property sellAvailableFlag флаг доступности продажи.
 * @property shortEnabledFlag флаг доступности шорта.
 * @property forIisFlag флаг доступности для ИИС.
 * @property forQualInvestorFlag флаг для квалифицированных инвесторов.
 * @property weekendFlag флаг выходных торгов.
 * @property blockedTcaFlag флаг блокировки TCA.
 */
sealed interface Instrument {
    val brokerName: BrokerName
    val tscalpInstrumentId: String
    val ticker: String
    val name: String
    val instrumentType: InstrumentType

    val classCode: String?
    val isin: String
    val figi: String?
    val exchange: String?
    val lot: Long?
    val currency: String?
    val settlementCurrency: String?
    val priceStepCost: Double?
    val minPriceIncrement: Double?
    val expirationDate: Instant?

    val apiTradeAvailableFlag: Boolean?
    val buyAvailableFlag: Boolean?
    val sellAvailableFlag: Boolean?
    val shortEnabledFlag: Boolean?
    val forIisFlag: Boolean?
    val forQualInvestorFlag: Boolean?
    val weekendFlag: Boolean?
    val blockedTcaFlag: Boolean?
}

/**
 * Фьючерс (proto T-Invest `Future` + BCS + Finam).
 *
 * Margin/risk-поля (10) — account-specific, будут вынесены в
 * `TradeCheckResult` задачей `### Margin/risk`.
 *
 * @property firstTradeDate дата начала торгов.
 * @property lastTradeDate дата окончания торгов.
 * @property futuresType тип фьючерса.
 * @property assetType тип базового актива.
 * @property basicAsset базовый актив.
 * @property basicAssetSize размер базового актива.
 * @property basicAssetPositionUid uid базового актива.
 * @property initialMarginOnBuy начальная маржа на покупку.
 * @property initialMarginOnSell начальная маржа на продажу.
 * @property dlong ставка начальной маржи long КСУР.
 * @property dshort ставка начальной маржи short КСУР.
 * @property dlongMin ставка начальной маржи long КПУР.
 * @property dshortMin ставка начальной маржи short КПУР.
 * @property dlongClient ставка начальной маржи long клиентская.
 * @property dshortClient ставка начальной маржи short клиентская.
 * @property klong коэффициент риска long.
 * @property kshort коэффициент риска short.
 */
data class Future(
    override val brokerName: BrokerName,
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val name: String,
    override val isin: String,
    override val classCode: String? = null,
    override val figi: String? = null,
    override val exchange: String? = null,
    override val lot: Long? = null,
    override val currency: String? = null,
    override val settlementCurrency: String? = null,
    override val priceStepCost: Double? = null,
    override val minPriceIncrement: Double? = null,
    override val expirationDate: Instant? = null,
    override val apiTradeAvailableFlag: Boolean? = null,
    override val buyAvailableFlag: Boolean? = null,
    override val sellAvailableFlag: Boolean? = null,
    override val shortEnabledFlag: Boolean? = null,
    override val forIisFlag: Boolean? = null,
    override val forQualInvestorFlag: Boolean? = null,
    override val weekendFlag: Boolean? = null,
    override val blockedTcaFlag: Boolean? = null,
    override val instrumentType: InstrumentType = InstrumentType.FUTURES,
    val firstTradeDate: Instant? = null,
    val lastTradeDate: Instant? = null,
    val futuresType: String? = null,
    val assetType: String? = null,
    val basicAsset: String? = null,
    val basicAssetSize: Double? = null,
    val basicAssetPositionUid: String? = null,
    val initialMarginOnBuy: Double? = null,
    val initialMarginOnSell: Double? = null,
    val dlong: Double? = null,
    val dshort: Double? = null,
    val dlongMin: Double? = null,
    val dshortMin: Double? = null,
    val dlongClient: Double? = null,
    val dshortClient: Double? = null,
    val klong: Double? = null,
    val kshort: Double? = null,
) : Instrument

/**
 * Акция (proto T-Invest `Share` + BCS + Finam).
 *
 * @property ipoDate дата IPO.
 * @property issueSize размер выпуска.
 * @property issueSizePlan плановый размер выпуска.
 * @property nominal номинал.
 * @property divYieldFlag флаг дивидендной доходности.
 * @property shareType тип акции.
 * @property liquidityFlag флаг ликвидности.
 */
data class Share(
    override val brokerName: BrokerName,
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val name: String,
    override val isin: String,
    override val classCode: String? = null,
    override val figi: String? = null,
    override val exchange: String? = null,
    override val lot: Long? = null,
    override val currency: String? = null,
    override val settlementCurrency: String? = null,
    override val priceStepCost: Double? = null,
    override val minPriceIncrement: Double? = null,
    override val expirationDate: Instant? = null,
    override val apiTradeAvailableFlag: Boolean? = null,
    override val buyAvailableFlag: Boolean? = null,
    override val sellAvailableFlag: Boolean? = null,
    override val shortEnabledFlag: Boolean? = null,
    override val forIisFlag: Boolean? = null,
    override val forQualInvestorFlag: Boolean? = null,
    override val weekendFlag: Boolean? = null,
    override val blockedTcaFlag: Boolean? = null,
    override val instrumentType: InstrumentType = InstrumentType.SHARE,
    val ipoDate: Instant? = null,
    val issueSize: Long? = null,
    val issueSizePlan: Long? = null,
    val nominal: Double? = null,
    val divYieldFlag: Boolean? = null,
    val shareType: String? = null,
    val liquidityFlag: Boolean? = null,
) : Instrument

/**
 * Облигация (proto T-Invest `Bond` + BCS `bond_details`).
 *
 * @property faceValue номинал.
 * @property couponRate ставка купона.
 * @property nextCoupon дата следующего купона.
 * @property couponsPerYear число купонов в год.
 * @property accruedInt накопленный купонный доход.
 * @property creditRating кредитный рейтинг.
 */
data class Bond(
    override val brokerName: BrokerName,
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val name: String,
    override val isin: String,
    override val classCode: String? = null,
    override val figi: String? = null,
    override val exchange: String? = null,
    override val lot: Long? = null,
    override val currency: String? = null,
    override val settlementCurrency: String? = null,
    override val priceStepCost: Double? = null,
    override val minPriceIncrement: Double? = null,
    override val expirationDate: Instant? = null,
    override val apiTradeAvailableFlag: Boolean? = null,
    override val buyAvailableFlag: Boolean? = null,
    override val sellAvailableFlag: Boolean? = null,
    override val shortEnabledFlag: Boolean? = null,
    override val forIisFlag: Boolean? = null,
    override val forQualInvestorFlag: Boolean? = null,
    override val weekendFlag: Boolean? = null,
    override val blockedTcaFlag: Boolean? = null,
    override val instrumentType: InstrumentType = InstrumentType.BOND,
    val faceValue: Double? = null,
    val couponRate: Double? = null,
    val nextCoupon: Instant? = null,
    val couponsPerYear: Long? = null,
    val accruedInt: Double? = null,
    val creditRating: String? = null,
) : Instrument

/**
 * Опцион (proto T-Invest `Option`; не реализуется сейчас, но модель
 * есть — иначе `when (instrument)` не exhaustive при появлении опционов).
 *
 * `expirationDate` — наследуется из интерфейса (nullable). По контракту
 * для опциона всегда non-null; Kotlin не позволяет сузить тип при
 * override — при необходимости отдельный контракт в Phase 3.
 *
 * @property direction PUT / CALL.
 * @property paymentType PREMIUM / MARGINAL.
 * @property style AMERICAN / EUROPEAN.
 * @property settlementType PHYSICAL_DELIVERY / CASH_SETTLEMENT.
 * @property strikePrice страйк.
 * @property firstTradeDate дата начала торгов.
 * @property lastTradeDate дата окончания торгов.
 */
data class Option(
    override val brokerName: BrokerName,
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val name: String,
    override val isin: String,
    override val classCode: String? = null,
    override val figi: String? = null,
    override val exchange: String? = null,
    override val lot: Long? = null,
    override val currency: String? = null,
    override val settlementCurrency: String? = null,
    override val priceStepCost: Double? = null,
    override val minPriceIncrement: Double? = null,
    override val expirationDate: Instant? = null,
    override val apiTradeAvailableFlag: Boolean? = null,
    override val buyAvailableFlag: Boolean? = null,
    override val sellAvailableFlag: Boolean? = null,
    override val shortEnabledFlag: Boolean? = null,
    override val forIisFlag: Boolean? = null,
    override val forQualInvestorFlag: Boolean? = null,
    override val weekendFlag: Boolean? = null,
    override val blockedTcaFlag: Boolean? = null,
    override val instrumentType: InstrumentType = InstrumentType.OPTION,
    val direction: String? = null,
    val paymentType: String? = null,
    val style: String? = null,
    val settlementType: String? = null,
    val strikePrice: Double? = null,
    val firstTradeDate: Instant? = null,
    val lastTradeDate: Instant? = null,
) : Instrument

/** ETF. Только core + common (subtype-specific нет). */
data class Etf(
    override val brokerName: BrokerName,
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val name: String,
    override val isin: String,
    override val classCode: String? = null,
    override val figi: String? = null,
    override val exchange: String? = null,
    override val lot: Long? = null,
    override val currency: String? = null,
    override val settlementCurrency: String? = null,
    override val priceStepCost: Double? = null,
    override val minPriceIncrement: Double? = null,
    override val expirationDate: Instant? = null,
    override val apiTradeAvailableFlag: Boolean? = null,
    override val buyAvailableFlag: Boolean? = null,
    override val sellAvailableFlag: Boolean? = null,
    override val shortEnabledFlag: Boolean? = null,
    override val forIisFlag: Boolean? = null,
    override val forQualInvestorFlag: Boolean? = null,
    override val weekendFlag: Boolean? = null,
    override val blockedTcaFlag: Boolean? = null,
    override val instrumentType: InstrumentType = InstrumentType.ETF,
) : Instrument

/** Валюта. Только core + common. */
data class Currency(
    override val brokerName: BrokerName,
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val name: String,
    override val isin: String,
    override val classCode: String? = null,
    override val figi: String? = null,
    override val exchange: String? = null,
    override val lot: Long? = null,
    override val currency: String? = null,
    override val settlementCurrency: String? = null,
    override val priceStepCost: Double? = null,
    override val minPriceIncrement: Double? = null,
    override val expirationDate: Instant? = null,
    override val apiTradeAvailableFlag: Boolean? = null,
    override val buyAvailableFlag: Boolean? = null,
    override val sellAvailableFlag: Boolean? = null,
    override val shortEnabledFlag: Boolean? = null,
    override val forIisFlag: Boolean? = null,
    override val forQualInvestorFlag: Boolean? = null,
    override val weekendFlag: Boolean? = null,
    override val blockedTcaFlag: Boolean? = null,
    override val instrumentType: InstrumentType = InstrumentType.CURRENCY,
) : Instrument

/** Прочее. Только core + common. */
data class Other(
    override val brokerName: BrokerName,
    override val tscalpInstrumentId: String,
    override val ticker: String,
    override val name: String,
    override val isin: String,
    override val classCode: String? = null,
    override val figi: String? = null,
    override val exchange: String? = null,
    override val lot: Long? = null,
    override val currency: String? = null,
    override val settlementCurrency: String? = null,
    override val priceStepCost: Double? = null,
    override val minPriceIncrement: Double? = null,
    override val expirationDate: Instant? = null,
    override val apiTradeAvailableFlag: Boolean? = null,
    override val buyAvailableFlag: Boolean? = null,
    override val sellAvailableFlag: Boolean? = null,
    override val shortEnabledFlag: Boolean? = null,
    override val forIisFlag: Boolean? = null,
    override val forQualInvestorFlag: Boolean? = null,
    override val weekendFlag: Boolean? = null,
    override val blockedTcaFlag: Boolean? = null,
    override val instrumentType: InstrumentType = InstrumentType.OTHER,
) : Instrument

/**
 * Стоимость одного пункта цены в рублях.
 *
 * Единая формула: `priceStepCost / minPriceIncrement`.
 * Для акций / облигаций / ETF — `1.0` (`priceStepCost = null`).
 * Для фьючерсов — реальный множитель.
 */
val Instrument.moneyPerPointOfPrice: Double
    get() {
        val amount = priceStepCost?.takeIf { it > 0.0 } ?: return 1.0
        val inc = minPriceIncrement?.takeIf { it > 0.0 } ?: return 1.0
        return amount / inc
    }

/**
 * Тип инструмента (универсальный для всех брокеров).
 *
 * Заполняется маппером по контексту (T-Invest: `Shares()` -> SHARE,
 * `Futures()` -> FUTURES) или `fromBrokerString` (REST/WS-строки).
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
         * Broker string -> [InstrumentType]. Case-insensitive.
         *
         * T-Invest (lower): share, bond, etf, currency, futures, option, fond.
         * BCS (UPPER): STOCK, FOREIGN_STOCK, BONDS, NOTES, ...
         * Finam (UPPER): STOCK, BOND, FUTURES, ...
         *
         * Map-based dispatch (24 keys / 14 types): `when` gives
         * cyclomatic complexity 15 (detekt max 14).
         */
        private val BROKER_TYPE_MAP: Map<String, InstrumentType> =
            mapOf(
                "share" to SHARE,
                "stock" to SHARE,
                "foreign_stock" to FOREIGN_SHARE,
                "bond" to BOND,
                "bonds" to BOND,
                "note" to NOTE,
                "notes" to NOTE,
                "euro_bond" to EURO_BOND,
                "euro_bonds" to EURO_BOND,
                "depositary_receipt" to DEPOSITARY_RECEIPT,
                "depositary_receipts" to DEPOSITARY_RECEIPT,
                "etf" to ETF,
                "mutual_fund" to MUTUAL_FUND,
                "mutual_funds" to MUTUAL_FUND,
                "fond" to MUTUAL_FUND,
                "futures" to FUTURES,
                "option" to OPTION,
                "options" to OPTION,
                "currency" to CURRENCY,
                "commodity" to COMMODITY,
                "goods" to COMMODITY,
                "index" to INDEX,
                "indices" to INDEX,
            )

        /** Broker string -> [InstrumentType]; unknown -> [OTHER]. */
        fun fromBrokerString(value: String): InstrumentType = BROKER_TYPE_MAP[value.lowercase()] ?: OTHER
    }
}
