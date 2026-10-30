package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerSnapshotApi
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Order
import com.gitlab.biomorf.tscalp.domain.models.StreamState
import com.gitlab.biomorf.tscalp.domain.models.toOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Агрегирует активные заявки счёта из двух источников:
 *
 * 1. **Snapshot polling** (`getOrders` + `getStopOrders`, gRPC unary,
 *    раз в [POLLING_INTERVAL]). Покрывает заявки, выставленные
 *    до открытия приложения (в т.ч. с другого устройства), и
 *    stop-orders (stream их не отдаёт).
 * 2. **Stream** (`subscribeOrderState`, gRPC-stream + WS fallback).
 *    Мгновенные события изменения статуса.
 *
 * Merge-семантика:
 * - polling: добавить/обновить элементы из снимка. Отсутствие
 *   заявки в снимке не удаляет её из кэша — она могла быть
 *   только что создана через stream, до того как сервер успел
 *   её вернуть в unary.
 * - stream: если [Order.status] терминальный
 *   ([TERMINAL_STATUSES]) — удалить из кэша. Иначе — добавить/обновить.
 *
 * Результат — `StateFlow<List<Order>>`, отсортированный
 * по [Order.orderDate] по возрастанию (новые снизу, как
 * в текущем `OrdersListViewModel`).
 *
 * Scope — single-threaded (`managerDispatcher.limitedParallelism(1)`),
 * поэтому доступ к кэшу не требует синхронизации: polling и
 * stream выполняются последовательно на одном worker'е.
 */
@Singleton
class OrderFlowManager
    @Inject
    constructor(
        private val brokerManager: BrokerRegistry,
        private val logger: Logger,
        private val clock: Clock,
        private val managerDispatcher: CoroutineDispatcher,
    ) {
        companion object {
            private const val TAG = "OrderFlowManager"
            private val POLLING_INTERVAL = 15.seconds
            private val STATE_TICK = 1.seconds
            private val TERMINAL_STATUSES = setOf("FILL", "CANCELLED", "REJECTED")
        }

        private val _orders = MutableStateFlow<List<Order>>(emptyList())
        val orders: StateFlow<List<Order>> = _orders.asStateFlow()

        /** true после первой итерации polling (успешной или с ошибкой). */
        private val _hasLoaded = MutableStateFlow(false)
        val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

        /** Трекер [StreamState]: LIVE / POLLING / STALE / STOPPED. */
        private val stateTracker = StreamStateTracker(logger = logger, tag = "OrderFlowManager", clock = clock)

        /** Состояние источника данных: LIVE / POLLING / STALE / STOPPED. */
        val connectionState: StateFlow<StreamState> = stateTracker.state

        private val cache = OrderCache(ttl = POLLING_INTERVAL * 2)
        private var job: Job? = null
        private var currentAccountId: String? = null

        /**
         * Защищает check-then-set в [start] / [stop] от race: два
         * параллельных вызова иначе проходят проверку идемпотентности
         * и создают по job'у (первый — orphan).
         */
        private val startLock = Any()

        private val scope =
            CoroutineScope(
                managerDispatcher.limitedParallelism(1) + SupervisorJob() +
                    CoroutineExceptionHandler { _, e ->
                        logger.e(TAG, "Uncaught error in order stream", e)
                    },
            )

        /**
         * Запускает агрегацию заявок для [accountId].
         * Идемпотентен для одного и того же accountId. При смене
         * счёта — перезапускает оба источника (polling и stream)
         * и очищает кэш.
         */
        fun start(accountId: String) =
            synchronized(startLock) {
                doStart(accountId, forceRestart = false)
            }

        private fun doStart(
            accountId: String,
            forceRestart: Boolean,
        ) {
            if (!forceRestart && job?.isActive == true && currentAccountId == accountId) {
                logger.d(TAG, "Поток заявок уже запущен для $accountId")
                return
            }
            val switching = currentAccountId != accountId
            if (switching) {
                logger.d(TAG, "Перезапуск потока заявок: $currentAccountId → $accountId")
            }
            job?.cancel()
            currentAccountId = accountId
            stateTracker.onStart()
            job =
                scope.launch {
                    if (switching) {
                        cache.clear()
                        _orders.value = emptyList()
                        _hasLoaded.value = false
                    }
                    val broker = brokerManager.getBroker(BrokerName.TINVEST)
                    if (broker == null || !broker.isInitialized) {
                        logger.e(TAG, "TInvestBrokerAPI не инициализирован, поток не запускаем")
                        stateTracker.onStop()
                        return@launch
                    }
                    val snapshotApi = broker.snapshot
                    if (snapshotApi == null) {
                        logger.e(TAG, "${broker.name.key}: *Snapshot не поддерживается, polling не запускаем")
                    }
                    coroutineScope {
                        launch { collectStream(broker, accountId) }
                        if (snapshotApi != null) {
                            launch { runPolling(snapshotApi, accountId) }
                        }
                        launch { stateWatchdog() }
                    }
                }
        }

        /**
         * Принудительно перезапускает агрегацию с текущими флагами
         * DebugConfig. Вызывается из UI при переключении тумблеров
         * (gRPC, WS, REST), чтобы применить их на лету.
         *
         * No-op, если менеджер не запущен (`currentAccountId == null`).
         * Симметрично `PositionFlowManager.reapplyDebugFlags`.
         */
        fun reapplyDebugFlags() =
            synchronized(startLock) {
                val accountId = currentAccountId ?: return@synchronized
                logger.d(TAG, "reapplyDebugFlags: restarting stream")
                doStart(accountId, forceRestart = true)
            }

        /**
         * Останавливает агрегацию и очищает кэш. Следующий [start]
         * снова поднимется с чистого листа.
         */
        fun stop() =
            synchronized(startLock) {
                job?.cancel()
                job = null
                currentAccountId = null
                stateTracker.onStop()
                scope.launch {
                    // Если между stop() и этой корутиной уже вызвали
                    // start() — не затираем свежие данные.
                    if (currentAccountId == null) {
                        cache.clear()
                        _orders.value = emptyList()
                        _hasLoaded.value = false
                    }
                }
            }

        /**
         * Polling-loop: раз в [POLLING_INTERVAL] (15 сек) снимает
         * `ordersSnapshot` (gRPC unary, fallback REST), обновляет
         * кэш и `_hasLoaded`. TTL-эвикция — только при успешном
         * цикле (если polling упал, `lastSeenAt` не обновляется
         * у валидных заявок, эвикция снесла бы их).
         *
         * `stateTracker.onPollingSuccess()` — после успешного
         * цикла: транспорт жив. INITIALIZING/STALE → POLLING.
         */
        private suspend fun runPolling(
            snapshotApi: BrokerSnapshotApi,
            accountId: String,
        ) {
            while (currentCoroutineContext().isActive) {
                try {
                    val orders = snapshotApi.ordersSnapshot(accountId)
                    logger.d(TAG, "polling: ${orders.size} orders")
                    val now = clock.now()
                    for (item in orders) {
                        cache.putFromPolling(item, now)
                    }
                    // Эвикция только при успешном цикле: если polling
                    // упал, lastSeenAt не обновляется у валидных
                    // заявок, и эвикция снесла бы их.
                    cache.evictStale(now)
                    _orders.value = cache.snapshot()
                    stateTracker.onPollingSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.w(TAG, "polling error: ${e.message}")
                } finally {
                    _hasLoaded.value = true
                }
                delay(POLLING_INTERVAL)
            }
        }

        /**
         * Stream-loop: подписка на `subscribeOrderState`
         * (gRPC-stream + WS fallback). На каждое событие:
         * `stateTracker.onStreamEmit()` (→ LIVE), обновление
         * кэша. Терминальные статусы ([TERMINAL_STATUSES]) —
         * удаление из кэша, остальные — `putFromStream`.
         */
        private suspend fun collectStream(
            broker: BrokerApi,
            accountId: String,
        ) {
            try {
                broker.streams?.subscribeOrderState(accountId)?.collect { state ->
                    stateTracker.onStreamEmit()
                    val item = state.toOrder()
                    logger.d(TAG, "stream: ${item.orderId} status=${item.status}")
                    if (item.status in TERMINAL_STATUSES) {
                        cache.remove(item.orderId)
                    } else {
                        cache.putFromStream(item, clock.now())
                    }
                    _orders.value = cache.snapshot()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "stream error: ${e.message}", e)
            }
        }

        /**
         * Watchdog: раз в [STATE_TICK] (1 сек) двигает
         * [StreamState] через `stateTracker.tick()`.
         */
        private suspend fun stateWatchdog() {
            while (currentCoroutineContext().isActive) {
                delay(STATE_TICK)
                stateTracker.tick()
            }
        }
    }
