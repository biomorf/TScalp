package com.gitlab.biomorf.tscalp.domain.api

import com.gitlab.biomorf.tscalp.data.api.TInvestBrokerAPI
import com.gitlab.biomorf.tscalp.di.BrokerManager
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.OrderListItem
import com.gitlab.biomorf.tscalp.domain.models.StreamState
import com.gitlab.biomorf.tscalp.domain.models.toOrderListItem
import com.gitlab.biomorf.tscalp.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/**
 * Агрегирует активные заявки счёта из двух источников:
 *
 * 1. **Snapshot polling** (`getOrders` + `getStopOrders`, gRPC unary,
 *    раз в [POLLING_INTERVAL_MS]). Покрывает заявки, выставленные
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
 * - stream: если [OrderListItem.status] терминальный
 *   ([TERMINAL_STATUSES]) — удалить из кэша. Иначе — добавить/обновить.
 *
 * Результат — `StateFlow<List<OrderListItem>>`, отсортированный
 * по [OrderListItem.orderDate] по возрастанию (новые снизу, как
 * в текущем `OrdersListViewModel`).
 *
 * Scope — single-threaded (`Dispatchers.IO.limitedParallelism(1)`),
 * поэтому доступ к кэшу не требует синхронизации: polling и
 * stream выполняются последовательно на одном worker'е.
 */
@Singleton
class OrderFlowManager
    @Inject
    constructor(
        private val brokerManager: BrokerManager,
    ) {
        companion object {
            private const val TAG = "OrderFlowManager"
            private const val POLLING_INTERVAL_MS = 15_000L
            private const val STATE_TICK_MS = 1_000L
            private val TERMINAL_STATUSES = setOf("FILL", "CANCELLED", "REJECTED")
        }

        private val _orders = MutableStateFlow<List<OrderListItem>>(emptyList())
        val orders: StateFlow<List<OrderListItem>> = _orders.asStateFlow()

        /** true после первой итерации polling (успешной или с ошибкой). */
        private val _hasLoaded = MutableStateFlow(false)
        val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

        private val stateTracker = StreamStateTracker(tag = "OrderFlowManager")

        /** Состояние источника данных: LIVE / POLLING / STALE / STOPPED. */
        val connectionState: StateFlow<StreamState> = stateTracker.state

        private val cache = OrderCache(ttlMs = POLLING_INTERVAL_MS * 2)
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
                Dispatchers.IO.limitedParallelism(1) + SupervisorJob() +
                    CoroutineExceptionHandler { _, e ->
                        AppLogger.e(TAG, "Uncaught error in order stream", e)
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
                doStart(accountId)
            }

        private fun doStart(accountId: String) {
            if (job?.isActive == true && currentAccountId == accountId) {
                AppLogger.d(TAG, "Поток заявок уже запущен для $accountId")
                return
            }
            val switching = currentAccountId != accountId
            if (switching) {
                AppLogger.d(TAG, "Перезапуск потока заявок: $currentAccountId → $accountId")
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
                    val broker = brokerManager.getBroker(BrokerName.TINVEST) as? TInvestBrokerAPI
                    if (broker == null || !broker.isInitialized) {
                        AppLogger.e(TAG, "TInvestBrokerAPI не инициализирован, поток не запускаем")
                        stateTracker.onStop()
                        return@launch
                    }
                    coroutineScope {
                        launch { collectPolling(broker, accountId) }
                        launch { collectStream(broker, accountId) }
                        launch { stateWatchdog() }
                    }
                }
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

        private suspend fun collectPolling(
            broker: TInvestBrokerAPI,
            accountId: String,
        ) {
            while (currentCoroutineContext().isActive) {
                try {
                    val snapshot = broker.ordersSnapshot(accountId)
                    AppLogger.d(TAG, "polling: ${snapshot.size} orders")
                    val now = System.currentTimeMillis()
                    for (item in snapshot) {
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
                    AppLogger.w(TAG, "polling error: ${e.message}")
                } finally {
                    _hasLoaded.value = true
                }
                delay(POLLING_INTERVAL_MS)
            }
        }

        private suspend fun collectStream(
            broker: TInvestBrokerAPI,
            accountId: String,
        ) {
            try {
                broker.subscribeOrderState(accountId).collect { state ->
                    stateTracker.onStreamEmit()
                    val item = state.toOrderListItem()
                    AppLogger.d(TAG, "stream: ${item.orderId} status=${item.status}")
                    if (item.status in TERMINAL_STATUSES) {
                        cache.remove(item.orderId)
                    } else {
                        cache.putFromStream(item, System.currentTimeMillis())
                    }
                    _orders.value = cache.snapshot()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "stream error: ${e.message}", e)
            }
        }

        private suspend fun stateWatchdog() {
            while (currentCoroutineContext().isActive) {
                delay(STATE_TICK_MS)
                stateTracker.tick()
            }
        }
    }
