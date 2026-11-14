package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Future
import com.gitlab.biomorf.tscalp.domain.models.OrderDirection
import com.gitlab.biomorf.tscalp.domain.models.Share
import com.gitlab.biomorf.tscalp.domain.models.TradingAvailability
import com.gitlab.biomorf.tscalp.presentation.theme.TScalpTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class OrdersScreensScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "\u0421\u0431\u0435\u0440\u0431\u0430\u043d\u043a",
            isin = "RU0009029540",
        )

    private val sberFuture =
        Future(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber-fut",
            ticker = "SBRF-3.24",
            name = "\u0421\u0431\u0435\u0440\u0431\u0430\u043d\u043a \u0444\u044c\u044e\u0447\u0435\u0440\u0441",
            isin = "RU000SBRF001",
        )

    private fun emptyForm() = OrderFormState()

    private fun emptyCoord() = OrdersCoordState()

    private fun emptySearch() = OrderSearchState()

    @Test
    fun searchResultItem_share() {
        rule.setContent {
            TScalpTheme {
                SearchResultItem(instrument = sber, onClick = {})
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun searchResultItem_future() {
        rule.setContent {
            TScalpTheme {
                SearchResultItem(instrument = sberFuture, onClick = {})
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun confirmDialog_buy() {
        val form =
            emptyForm().copy(
                showConfirmDialog = true,
                selectedInstrument = sber,
                pendingDirection = OrderDirection.BUY,
                quantity = "10",
                executionPrice = 150.0,
            )
        rule.setContent {
            TScalpTheme {
                OrdersConfirmDialogSection(
                    instrumentState = form.toInstrumentStateForTest(),
                    inputState = form.toInputStateForTest(),
                    tradeDetailsState = form.toTradeDetailsStateForTest(),
                    executionState = form.toExecutionStateForTest(),
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun confirmDialog_sell() {
        val form =
            emptyForm().copy(
                showConfirmDialog = true,
                selectedInstrument = sber,
                pendingDirection = OrderDirection.SELL,
                quantity = "10",
                executionPrice = 150.0,
            )
        rule.setContent {
            TScalpTheme {
                OrdersConfirmDialogSection(
                    instrumentState = form.toInstrumentStateForTest(),
                    inputState = form.toInputStateForTest(),
                    tradeDetailsState = form.toTradeDetailsStateForTest(),
                    executionState = form.toExecutionStateForTest(),
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun instrumentSection_empty() {
        rule.setContent {
            TScalpTheme {
                OrdersInstrumentSection(
                    coordState = emptyCoord(),
                    searchState = emptySearch(),
                    instrumentState = emptyForm().toInstrumentStateForTest(),
                    tradingStatuses = emptyMap(),
                    resetSwipe = false,
                    onSearchQueryChanged = {},
                    onInstrumentSelected = {},
                    onClearSearch = {},
                    onRefreshSearch = {},
                    onOpenSearchSettings = {},
                    onClearSelected = {},
                    onOpenBrokerDialog = { _, _ -> },
                )
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun instrumentSection_results() {
        val search =
            emptySearch().copy(
                primary =
                    SlotSearchState(
                        query = "SBER",
                        results = listOf(sber, sberFuture),
                    ),
                isSearchActive = true,
            )
        rule.setContent {
            TScalpTheme {
                OrdersInstrumentSection(
                    coordState = emptyCoord(),
                    searchState = search,
                    instrumentState = emptyForm().toInstrumentStateForTest(),
                    tradingStatuses = emptyMap(),
                    resetSwipe = false,
                    onSearchQueryChanged = {},
                    onInstrumentSelected = {},
                    onClearSearch = {},
                    onRefreshSearch = {},
                    onOpenSearchSettings = {},
                    onClearSelected = {},
                    onOpenBrokerDialog = { _, _ -> },
                )
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun pairSection_enabled() {
        val form =
            emptyForm().copy(
                pairTradingEnabled = true,
                selectedInstrument = sber,
                pairedInstrument = sberFuture,
                pairedMultiplier = "10",
            )
        rule.setContent {
            TScalpTheme {
                Column {
                    OrdersPairSection(
                        coordState = emptyCoord(),
                        searchState = emptySearch(),
                        instrumentState = form.toInstrumentStateForTest(),
                        inputState = form.toInputStateForTest(),
                        tradeDetailsState = form.toTradeDetailsStateForTest(),
                        tradingStatuses = emptyMap(),
                        resetSwipe = false,
                        scrollState = rememberScrollState(),
                        scope = CoroutineScope(Dispatchers.Unconfined),
                        onPairSearchQueryChanged = {},
                        onPairedInstrumentSelected = {},
                        onClearPairSearch = {},
                        onRefreshPairSearch = {},
                        onOpenPairSearchBrokerSettings = {},
                        onPairTradingEnabledChanged = {},
                        onClearPairedInstrument = {},
                        onOpenBrokerDialog = { _, _ -> },
                        onPairedMultiplierChanged = {},
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage()
    }

    @Test
    fun scrollableContent_filled() {
        val form =
            emptyForm().copy(
                selectedInstrument = sber,
                quantity = "10",
                currentPrice = 150.0,
                executionPrice = 150.0,
                costOverlay = "1 500 \u20bd",
                multiplierOverlay = "x1",
            )
        val scrollable =
            form.toScrollableStateForTest(
                coordState = emptyCoord(),
                searchState = emptySearch(),
                tradingStatuses = emptyMap(),
                resetSwipe = false,
            )
        rule.setContent {
            TScalpTheme {
                Column {
                    OrdersScrollableContent(
                        state = scrollable,
                        callbacks =
                            OrdersScrollableCallbacks(
                                onSearchQueryChanged = {},
                                onInstrumentSelected = {},
                                onClearSearch = {},
                                onRefreshSearch = {},
                                onOpenSearchSettings = {},
                                onClearSelected = {},
                                onOpenBrokerDialog = { _, _, _ -> },
                                onDismissConfirmDialog = {},
                                onConfirmOrder = {},
                                onQuantityChanged = {},
                                onOrderTypeChanged = {},
                                onLimitPriceChanged = {},
                                onStopPriceChanged = {},
                                onPairSearchQueryChanged = {},
                                onPairedInstrumentSelected = {},
                                onClearPairSearch = {},
                                onRefreshPairSearch = {},
                                onOpenPairSearchBrokerSettings = {},
                                onPairTradingEnabledChanged = {},
                                onClearPairedInstrument = {},
                                onPairedMultiplierChanged = {},
                            ),
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage()
    }
    // ==================== test-only helpers (Phase 5f-b-2c, 2d) ====================
    //
    // Дублируют prod-логику бывших OrdersScrollableAdapters (удалён в 5f-b-2c).
    // Умирают в Phase 7 вместе с OrderFormState / baseline-тестом
    // (тогда baseline перейдёт на sub-state напрямую).

    private fun OrderFormState.toInstrumentStateForTest() =
        OrderInstrumentState(
            ticker = ticker,
            selectedInstrument = selectedInstrument,
            currentPrice = currentPrice,
            selectedPriceChangePercent = selectedPriceChangePercent,
            currentPointValue = currentPointValue,
            isPriceLoading = isPriceLoading,
            selectedBroker = selectedBroker,
            selectedAccountId = selectedAccountId,
            pairedInstrument = pairedInstrument,
            pairCurrentPrice = pairCurrentPrice,
            pairedPointValue = pairedPointValue,
            pairedBroker = pairedBroker,
            pairedAccountId = pairedAccountId,
        )

    private fun OrderFormState.toInputStateForTest() =
        OrderInputState(
            quantity = quantity,
            limitPrice = limitPrice,
            stopPrice = stopPrice,
            orderType = orderType,
            expirationType = expirationType,
            expireDate = expireDate,
            pairTradingEnabled = pairTradingEnabled,
            pairedMultiplier = pairedMultiplier,
        )

    private fun OrderFormState.toTradeDetailsStateForTest() =
        OrderTradeDetailsState(
            executionPrice = executionPrice,
            costOverlay = costOverlay,
            multiplierOverlay = multiplierOverlay,
            pairExecPrice = pairExecPrice,
            pairedOrderType = pairedOrderType,
        )

    private fun OrderFormState.toExecutionStateForTest() =
        OrderExecutionState(
            showConfirmDialog = showConfirmDialog,
            pendingDirection = pendingDirection,
            confirmOrdersEnabled = confirmOrdersEnabled,
            isLoading = isLoading,
            freeBalance = freeBalance,
        )

    private fun OrderFormState.toScrollableStateForTest(
        coordState: OrdersCoordState,
        searchState: OrderSearchState,
        tradingStatuses: Map<String, TradingAvailability>,
        resetSwipe: Boolean,
    ) = OrdersScrollableState(
        coordState = coordState,
        searchState = searchState,
        instrumentState = toInstrumentStateForTest(),
        inputState = toInputStateForTest(),
        tradeDetailsState = toTradeDetailsStateForTest(),
        executionState = toExecutionStateForTest(),
        tradingStatuses = tradingStatuses,
        resetSwipe = resetSwipe,
    )
}
