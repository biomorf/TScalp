package com.gitlab.biomorf.tscalp.presentation.screens.orders

import androidx.compose.runtime.Composable
import com.gitlab.biomorf.tscalp.presentation.components.OrdersListDialog

/**
 * Все 4 диалога экрана «Заявки»: TradingBrokerDialog, OrdersListDialog,
 * SearchBrokerDialog, PairSearchBrokerDialog.
 *
 * **2c-фиксация (Pass-1):** принимает `*ViewModel` параметрами (не 15
 * callbacks), чтобы extract был mobile-safe: state / effects / remember
 * остаются в `OrdersScreen`, child не трогает `remember` / `LaunchedEffect`.
 * Плюсы: одна замена 4 блоков → 1 вызов, 0 behavioral-риска. Минусы:
 * child-composable знает про VM (норма MVVM: VM = presentation). Pass-2
 * пересмотрит на callbacks при state hoisting.
 *
 * `showOrdersListDialog` + `onShowOrdersListDialogChange` — state остаётся
 * в `OrdersScreen` (Pass-1 не делает state hoisting).
 */
@Composable
internal fun OrdersDialogsSection(
    dialogsViewModel: OrderDialogsViewModel,
    searchViewModel: OrderSearchViewModel,
    formViewModel: OrderFormViewModel,
    dialogsState: OrderDialogsState,
    searchState: OrderSearchState,
    showOrdersListDialog: Boolean,
    onShowOrdersListDialogChange: (Boolean) -> Unit,
) {
    TradingBrokerDialog(
        availableBrokers = dialogsViewModel.getAvailableBrokerNames(),
        dialogsState = dialogsState,
        onBrokerSelected = { dialogsViewModel.onBrokerSelected(it) },
        onAccountSelected = { dialogsViewModel.onAccountSelectedDialog(it) },
        onDismiss = { dialogsViewModel.closeBrokerDialog() },
        onSave = {
            dialogsViewModel.saveBrokerSettings { slot, newAccountId ->
                when (slot) {
                    CardSlot.PRIMARY -> formViewModel.onAccountSaved(newAccountId)
                    CardSlot.PAIR -> formViewModel.onPairedAccountSaved(newAccountId)
                }
            }
        },
    )

    if (showOrdersListDialog) {
        OrdersListDialog(onDismiss = { onShowOrdersListDialogChange(false) })
    }

    SearchBrokerDialog(
        availableBrokers = searchViewModel.getAvailableBrokerNames(),
        searchState = searchState,
        onBrokerSelected = { searchViewModel.saveBroker(SearchSlot.PRIMARY, it) },
        onDismiss = { searchViewModel.dismissBrokerDialog(SearchSlot.PRIMARY) },
    )

    PairSearchBrokerDialog(
        availableBrokers = searchViewModel.getAvailableBrokerNames(),
        searchState = searchState,
        onBrokerSelected = { searchViewModel.saveBroker(SearchSlot.PAIR, it) },
        onDismiss = { searchViewModel.dismissBrokerDialog(SearchSlot.PAIR) },
    )
}
