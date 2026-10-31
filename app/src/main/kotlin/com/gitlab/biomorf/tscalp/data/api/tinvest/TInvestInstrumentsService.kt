package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.InstrumentUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.FindInstrumentRequest
import ru.tinkoff.piapi.contract.v1.GetFuturesMarginRequest
import ru.tinkoff.piapi.contract.v1.Instrument
import ru.tinkoff.piapi.contract.v1.InstrumentIdType
import ru.tinkoff.piapi.contract.v1.InstrumentRequest
import ru.tinkoff.piapi.contract.v1.InstrumentShort

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
) {
    companion object {
        private const val TAG = "TInvestInstruments"
    }

    suspend fun fetchFullInstrument(uid: String): InstrumentUi? {
        val protoInstrument = fetchProtoInstrument(uid) ?: return null
        var marginAmount: Double? = null
        if (protoInstrument.instrumentType == "futures" && protoInstrument.figi != null) {
            marginAmount = getFuturesMargin(protoInstrument.figi)
        }
        return TInvestInstrumentMapper.mapProtoToDomain(protoInstrument, marginAmount)
    }

    internal suspend fun fetchProtoInstrument(uid: String): Instrument? =
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
            currentApi.instrumentsServiceSync.getInstrumentBy(request).instrument
        }

    @Suppress("DEPRECATION")
    internal suspend fun getFuturesMargin(figi: String): Double? =
        try {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = GetFuturesMarginRequest.newBuilder().setFigi(figi).build()
            val response = currentApi.instrumentsServiceSync.getFuturesMargin(request)
            response.minPriceIncrementAmount?.let { it.units + it.nano / 1_000_000_000.0 }
        } catch (e: Exception) {
            logger.w(TAG, "Не удалось получить стоимость шага цены для $figi: ${e.message}")
            null
        }

    private suspend fun findInstrumentShorts(query: String): List<InstrumentShort> =
        withContext(Dispatchers.IO) {
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = FindInstrumentRequest.newBuilder().setQuery(query).build()
            currentApi.instrumentsServiceSync.findInstrument(request).instrumentsList
        }

    suspend fun findInstruments(query: String): List<InstrumentUi> =
        withContext(Dispatchers.IO) {
            findInstrumentShorts(query).mapNotNull { short ->
                try {
                    val instrument = fetchProtoInstrument(short.uid) ?: return@mapNotNull null
                    TInvestInstrumentMapper.mapProtoToDomain(instrument)
                } catch (e: Exception) {
                    logger.e(TAG, "Ошибка получения инструмента по uid=${short.uid}", e)
                    null
                }
            }
        }
}
