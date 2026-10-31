package com.gitlab.biomorf.tscalp.data.api.tinvest

import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.PositionStreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.tinkoff.piapi.contract.v1.PositionsFutures
import ru.tinkoff.piapi.contract.v1.PositionsSecurities
import ru.tinkoff.piapi.contract.v1.PositionsStreamRequest
import ru.tinkoff.piapi.contract.v1.PositionsStreamResponse

/**
 * Стримы позиций T-Invest (gRPC + reconnect).
 *
 * Выделен из `TInvestMarketDataService` (clean-audit Н63, шаг 7).
 * Отвечает только за gRPC-стрим позиций: `subscribePositionsGrpc`
 * (одиночная попытка) и `subscribePositionsGrpcWithReconnect`
 * (переподключение с backoff). Polling и P&L-обогащение —
 * в `PositionFlowManager`.
 */
class TInvestPositionsService(
    private val state: TInvestApiState,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "TInvestPositions"

        /** Интервал polling позиций. */
        private const val RETRY_DELAY_MS = 10_000L

        /**
         * Экспоненциальный backoff для переподключения gRPC-стрима.
         * Cap 60 секунд — по аналогии с [PriceFlowManager].
         */
        private val GRPC_RETRY_DELAYS_MS = longArrayOf(1_000, 5_000, 15_000, 30_000, 60_000)
    }

    // ---------- Счета ----------

    // ---------- Стрим позиций (gRPC) ----------

    /**
     * gRPC-стрим изменений позиций через
     * `OperationsStreamServiceAsync.positionsStream`.
     *
     * Возвращает `null`, если API не инициализирован.
     *
     * Даёт только изменения количества и идентификаторы.
     * Поля `currentPrice`, `averagePositionPrice`, `expectedYield`,
     * `pointValue` в стриме отсутствуют. Обогащение P&L —
     * задача `PositionFlowManager` (см. `docs/streams.md`).
     *
     * `withInitialPositions = true` — сервер пришлёт снапшот
     * позиций сразу при подключении, отдельный REST-запрос
     * не нужен.
     */
    fun subscribePositionsGrpc(accountId: String): Flow<PositionStreamItem>? {
        val api = state.api ?: return null
        return callbackFlow {
            val request =
                PositionsStreamRequest
                    .newBuilder()
                    .addAccounts(accountId)
                    .setWithInitialPositions(true)
                    .build()

            val streamJob =
                launch {
                    try {
                        api.operationsStreamServiceAsync.positionsStream(
                            request,
                            { response: PositionsStreamResponse ->
                                logger.d(
                                    TAG,
                                    "[stream/gRPC/TInvest]: response — " +
                                        "subs=${response.hasSubscriptions()}, " +
                                        "init=${response.hasInitialPositions()}, " +
                                        "pos=${response.hasPosition()}, " +
                                        "ping=${response.hasPing()}, " +
                                        "case=${response.payloadCase}",
                                )
                                if (response.hasInitialPositions()) {
                                    val snapshot = response.initialPositions
                                    snapshot.securitiesList.forEach { trySend(convertSecurity(it)) }
                                    snapshot.futuresList.forEach { trySend(convertFuture(it)) }
                                }
                                if (response.hasPosition()) {
                                    val data = response.position
                                    data.securitiesList.forEach { trySend(convertSecurity(it)) }
                                    data.futuresList.forEach { trySend(convertFuture(it)) }
                                }
                                // hasPing / hasSubscriptions — служебные, игнорируем.
                            },
                        )
                    } catch (e: Exception) {
                        close(e)
                    }
                }

            awaitClose { streamJob.cancel() }
        }
    }

    private fun convertSecurity(s: PositionsSecurities): PositionStreamItem =
        PositionStreamItem(
            tscalpInstrumentId = s.instrumentUid,
            brokerName = BrokerName.TINVEST,
            ticker = s.ticker,
            quantity = s.balance,
            classCode = s.classCode,
            instrumentType = s.instrumentType.ifBlank { "share" },
        )

    private fun convertFuture(f: PositionsFutures): PositionStreamItem =
        PositionStreamItem(
            tscalpInstrumentId = f.instrumentUid,
            brokerName = BrokerName.TINVEST,
            ticker = f.ticker,
            quantity = f.balance,
            classCode = f.classCode,
            instrumentType = "futures",
        )

    // ---------- Стрим позиций (параллельно) ----------

    /**
     * gRPC-стрим с автопереподключением.
     *
     * Возвращает `null`, если API не инициализирован.
     * Переподключается с экспоненциальным backoff
     * [GRPC_RETRY_DELAYS_MS] при ошибках. Штатное завершение
     * сервером → повтор без задержки. NOT_FOUND → завершение
     * потока без переподключения.
     */
    internal fun subscribePositionsGrpcWithReconnect(accountId: String): Flow<PositionStreamItem> =
        flow {
            var attempt = 0
            while (currentCoroutineContext().isActive) {
                val grpcFlow = subscribePositionsGrpc(accountId)
                if (grpcFlow == null) {
                    logger.d(TAG, "[stream/gRPC/TInvest]: waiting for API init")
                    delay(1_000)
                    continue
                }
                try {
                    logger.d(TAG, "[stream/gRPC/TInvest]: connecting (attempt=${attempt + 1})")
                    grpcFlow.collect { emit(it) }
                    attempt = 0
                    logger.d(TAG, "[stream/gRPC/TInvest]: completed by server, reconnecting")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e.message?.contains("NOT_FOUND") == true) {
                        logger.w(TAG, "[stream/gRPC/TInvest]: fatal (${e.message}) — stopping")
                        return@flow
                    }
                    attempt++
                    val delayMs = retryDelayMs(attempt)
                    logger.w(
                        TAG,
                        "[stream/gRPC/TInvest]: error (attempt=$attempt, ${e.message}), retry in ${delayMs}ms",
                    )
                    delay(delayMs)
                }
            }
        }

    private fun retryDelayMs(attempt: Int): Long {
        val idx = (attempt - 1).coerceIn(0, GRPC_RETRY_DELAYS_MS.lastIndex)
        return GRPC_RETRY_DELAYS_MS[idx]
    }
}
