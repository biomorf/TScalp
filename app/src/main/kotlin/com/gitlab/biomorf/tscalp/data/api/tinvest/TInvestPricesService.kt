package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.log.Logger
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest
import ru.tinkoff.piapi.contract.v1.LastPriceInstrument
import ru.tinkoff.piapi.contract.v1.MarketDataResponse
import ru.tinkoff.piapi.contract.v1.MarketDataServerSideStreamRequest
import ru.tinkoff.piapi.contract.v1.MarketDataStreamServiceGrpc
import ru.tinkoff.piapi.contract.v1.SubscribeLastPriceRequest
import ru.tinkoff.piapi.contract.v1.SubscriptionAction

/**
 * Цены T-Invest: unary-запрос (GetLastPrices) и gRPC-стрим последних цен.
 *
 * Выделен из `TInvestMarketDataService` (clean-audit Н63, шаг 5).
 * Не путать с `TInvestOrchestrator.pricesStream` — там композиция
 * gRPC/WS/REST fallback; здесь — транспортный метод.
 */
class TInvestPricesService(
    private val state: TInvestApiState,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "TInvestPrices"
    }

    suspend fun getLastPricesByTscalpInstrumentId(uids: List<String>): Map<String, Double?> =
        withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext emptyMap()
            val currentApi = state.api ?: throw IllegalStateException("API не инициализирован")
            val request = GetLastPricesRequest.newBuilder().addAllInstrumentId(uids).build()
            logger.d(TAG, "[unary/gRPC/TInvest] GetLastPrices for ${uids.size} instruments")
            val response = currentApi.marketDataServiceSync.getLastPrices(request)
            response.lastPricesList.associate { lp ->
                lp.instrumentUid to (lp.price?.let { it.units + it.nano / 1_000_000_000.0 })
            }
        }

    fun subscribeLastPrices(uids: List<String>): Flow<Pair<String, Double>> =
        callbackFlow {
            val channel =
                state.channels?.pricesStream
                    ?: throw IllegalStateException("gRPC-стрим не инициализирован")
            val stub = MarketDataStreamServiceGrpc.newStub(channel)

            val instruments = uids.map { LastPriceInstrument.newBuilder().setInstrumentId(it).build() }
            val subscribe =
                SubscribeLastPriceRequest
                    .newBuilder()
                    .setSubscriptionAction(SubscriptionAction.SUBSCRIPTION_ACTION_SUBSCRIBE)
                    .addAllInstruments(instruments)
                    .build()
            val serverSideRequest =
                MarketDataServerSideStreamRequest
                    .newBuilder()
                    .setSubscribeLastPriceRequest(subscribe)
                    .build()

            val responseObserver =
                object : StreamObserver<MarketDataResponse> {
                    override fun onNext(value: MarketDataResponse) {
                        if (value.hasLastPrice()) {
                            val lp = value.lastPrice
                            val price = lp.price?.let { it.units + it.nano / 1_000_000_000.0 }
                            if (price != null) {
                                logger.d(TAG, "[stream/gRPC/TInvest] LastPrice ${lp.instrumentUid} = $price")
                                trySend(lp.instrumentUid to price)
                            }
                        }
                    }

                    override fun onError(t: Throwable) {
                        close(t)
                    }

                    override fun onCompleted() {
                        close()
                    }
                }

            stub.marketDataServerSideStream(serverSideRequest, responseObserver)
            awaitClose { }
        }
}
