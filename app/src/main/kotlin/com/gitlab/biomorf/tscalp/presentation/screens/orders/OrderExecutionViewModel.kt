package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderOutcome
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderRequest
import com.gitlab.biomorf.tscalp.domain.usecases.PlaceOrderUseCase
import com.gitlab.biomorf.tscalp.presentation.util.StatusEvent
import com.gitlab.biomorf.tscalp.presentation.util.formatDomesticCurrency
import com.gitlab.biomorf.tscalp.presentation.util.formatPostOrderSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Execution sub-VM (C2, PR-4 Phase 3).
 *
 * **onBuyClick / onSellClick** всегда открывают confirm dialog (respect
 * `confirmOrdersEnabled` — на UI: при `false` UI сразу строит request и
 * вызывает `onConfirmOrder(request)` без диалога). Так Phase 3 не зависит
 * от построения request (это Phase 4 через `toPlaceOrderRequest()` +
 * Mediator).
 *
 * **2d-fixation (Phase 3 → closed Phase 3.1):** в `handlePostOrder*` не
 * пишем в Input state (`quantity`/`limitPrice`/`stopPrice` — не
 * очищаем). `freeBalance` — пишется в **свой** state (Phase 3.1,
 * 2026-11-14: execution-результат, а не trade-details). Старый
 * `OrderFormViewModel` работает параллельно до Phase 5.
 */
@HiltViewModel
class OrderExecutionViewModel
    @Inject
    constructor(
        private val placeOrderUseCase: PlaceOrderUseCase,
        private val tradingPreferencesRepository: TradingPreferencesRepository,
        @Suppress("unused") private val mediator: OrderFormMediator,
        private val logger: Logger,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OrderExecutionState())
        val state: StateFlow<OrderExecutionState> = _state.asStateFlow()

        private val _status = Channel<StatusEvent>(Channel.BUFFERED)
        val status: Flow<StatusEvent> = _status.receiveAsFlow()

        init {
            viewModelScope.launch {
                logger.d(TAG, "subscribed to confirmOrdersEnabledFlow")
                tradingPreferencesRepository.confirmOrdersEnabledFlow().collect { enabled ->
                    _state.update { it.copy(confirmOrdersEnabled = enabled) }
                }
            }
        }

        fun onBuyClick() {
            _state.update { it.copy(showConfirmDialog = true, pendingDirection = OrderDirection.BUY) }
        }

        fun onSellClick() {
            _state.update { it.copy(showConfirmDialog = true, pendingDirection = OrderDirection.SELL) }
        }

        fun onConfirmOrder(request: PlaceOrderRequest) {
            val direction = _state.value.pendingDirection ?: return
            _state.update { it.copy(showConfirmDialog = false, pendingDirection = null) }
            viewModelScope.launch { postOrder(request, direction) }
        }

        fun onDismissConfirmDialog() {
            _state.update { it.copy(showConfirmDialog = false, pendingDirection = null) }
        }

        private suspend fun postOrder(
            request: PlaceOrderRequest,
            direction: OrderDirection,
        ) {
            _state.update { it.copy(isLoading = true) }
            when (val outcome = placeOrderUseCase.execute(request, direction)) {
                is PlaceOrderOutcome.Success -> handlePostOrderSuccess(outcome)
                is PlaceOrderOutcome.InsufficientFunds -> handlePostOrderInsufficient(outcome)
                is PlaceOrderOutcome.Error -> handlePostOrderError(outcome)
                is PlaceOrderOutcome.InvalidRequest -> {
                    logger.w(TAG, "postOrder: invalid request - ${outcome.reason}")
                    _state.update { it.copy(isLoading = false) }
                    _status.trySend(StatusEvent("\u274c ${outcome.reason}", isError = true))
                }
            }
        }

        private fun handlePostOrderSuccess(outcome: PlaceOrderOutcome.Success) {
            var message = formatPostOrderSuccess(outcome.primary, outcome.paired)
            val freeBalance = outcome.freeBalance
            logger.d(
                TAG,
                "postOrder success: primary=${outcome.primary}, paired=${outcome.paired}, freeBalance=$freeBalance",
            )
            if (freeBalance != null && freeBalance < 1000.0) {
                message += "\n\u26a0\ufe0f \u041d\u0438\u0437\u043a\u0438\u0439 \u0441\u0432\u043e\u0431\u043e\u0434\u043d\u044b\u0439 \u043e\u0441\u0442\u0430\u0442\u043e\u043a: ${formatDomesticCurrency(freeBalance)}"
            }
            _state.update { it.copy(isLoading = false, freeBalance = outcome.freeBalance) }
            _status.trySend(StatusEvent(message, isError = false))
        }

        private fun handlePostOrderInsufficient(outcome: PlaceOrderOutcome.InsufficientFunds) {
            logger.w(
                TAG,
                "postOrder insufficient: balance=${outcome.balance}, required=${outcome.required}",
            )
            _state.update { it.copy(isLoading = false) }
            _status.trySend(
                StatusEvent(
                    "\u274c \u041d\u0435\u0434\u043e\u0441\u0442\u0430\u0442\u043e\u0447\u043d\u043e \u0441\u0440\u0435\u0434\u0441\u0442\u0432. \u0421\u0432\u043e\u0431\u043e\u0434\u043d\u043e: ${formatDomesticCurrency(outcome.balance)}, \u0442\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044f: ~${formatDomesticCurrency(outcome.required)}",
                    isError = true,
                ),
            )
        }

        private fun handlePostOrderError(outcome: PlaceOrderOutcome.Error) {
            logger.e(TAG, "postOrder failed: ${outcome.message}", outcome.cause)
            _state.update { it.copy(isLoading = false) }
            _status.trySend(StatusEvent("\u274c ${outcome.message}", isError = true))
        }

        companion object {
            private const val TAG = "OrderExecutionViewModel"
        }
    }
