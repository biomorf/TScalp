package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.data.api.common.TransportProtocol
import com.gitlab.biomorf.tscalp.data.api.common.TransportRateLimiter
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument
import com.gitlab.biomorf.tscalp.domain.models.InstrumentType
import com.gitlab.biomorf.tscalp.domain.models.SearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.FindInstrumentRequest
import ru.tinkoff.piapi.contract.v1.GetFuturesMarginRequest
import ru.tinkoff.piapi.contract.v1.InstrumentIdType
import ru.tinkoff.piapi.contract.v1.InstrumentRequest
import ru.tinkoff.piapi.contract.v1.InstrumentShort
import ru.tinkoff.piapi.contract.v1.Instrument as ProtoInstrument

/**
 * Инструменты T-Invest: полный инструмент по uid, поиск, стоимость шага цены.
 * Работает на разделяемом [TInvestApiState].
 *
 * Вызывается из [TInvestBrokerApi] (делегаты `findInstruments`,
 * `fetchFullInstrument`) и из [TInvestMarketDataService]
 * ([fetchProtoInstrument], [getFuturesMargin] — обогащение позиций).
 * Направление `portfolio → instruments` — обогащение данными инструментов.
 */
class TInvestInstrumentsService(
    private val state: TInvestApiState,
    private val logger: Logger,
    private val rateLimiter: TransportRateLimiter,
) {
    companion object {
        private const val TAG = "TInvestInstruments"
    }

    suspend fun fetchFullInstrument(uid: String): Instrument? {
        val protoInstrument = fetchProtoInstrument(uid) ?: return null
        var marginAmount: Double? = null
        if (protoInstrument.instrumentType == "futures" && protoInstrument.figi != null) {
            marginAmount = getFuturesMargin(protoInstrument.figi)
        }
        return TInvestInstrumentMapper.mapProtoToDomain(protoInstrument, marginAmount)
    }

    internal suspend fun fetchProtoInstrument(uid: String): ProtoInstrument? =
        withContext(Dispatchers.IO) {
            val currentApi =
                state.api ?: run {
                    logger.w(TAG, "fetchProtoInstrument: API не инициализирован, uid=$uid")
                    return@withContext null
                }
            val request =
                InstrumentRequest
                    .newBuilder()
                    .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_UID)
                    .setId(uid)
                    .build()
            rateLimiter.withUnaryPermission(
                protocol = TransportProtocol.GRPC,
                service = "instruments",
                method = "getInstrumentBy",
            ) {
                currentApi.instrumentsServiceSync.getInstrumentBy(request).instrument
            }
        }

    @Suppress("DEPRECATION")
    internal suspend fun getFuturesMargin(figi: String): Double? =
        try {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = GetFuturesMarginRequest.newBuilder().setFigi(figi).build()
            val response =
                rateLimiter.withUnaryPermission(
                    protocol = TransportProtocol.GRPC,
                    service = "instruments",
                    method = "getFuturesMargin",
                ) {
                    currentApi.instrumentsServiceSync.getFuturesMargin(request)
                }
            response.minPriceIncrementAmount?.let { it.units + it.nano / 1_000_000_000.0 }
        } catch (e: RateLimitTimeoutException) {
            logger.e(TAG, "getFuturesMargin($figi): rate-limited, ${e.message}", e)
            null
        } catch (e: Exception) {
            logger.w(TAG, "Не удалось получить стоимость шага цены для $figi: ${e.message}")
            null
        }

    private suspend fun findInstrumentShorts(query: String): List<InstrumentShort> =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
            rateLimiter.withUnaryPermission(
                protocol = TransportProtocol.GRPC,
                service = "instruments",
                method = "findInstrument",
            ) {
                currentApi.instrumentsServiceSync.findInstrument(request).instrumentsList
            }
        }

    suspend fun findInstruments(query: String): List<SearchItem> =
        withContext(Dispatchers.IO) {
            findInstrumentShorts(query).map { short ->
                SearchItem(
                    tscalpInstrumentId = short.uid,
                    brokerName = BrokerName.TINVEST,
                    ticker = short.ticker,
                    name = short.name,
                    instrumentType = InstrumentType.fromBrokerString(short.instrumentType ?: ""),
                    figi = short.figi.ifBlank { null },
                    classCode = short.classCode.ifBlank { null },
                )
            }
        }
}
