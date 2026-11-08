package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument

/**
 * Состояние диалога выбора брокера и счёта для карточки инструмента.
 *
 * Вынесено из `OrdersUiState` в рамках split `OrdersViewModel`
 * (M013 / A2, шаг 12a). Семантика полей 1:1 с прежним `OrdersUiState`:
 * - [showBrokerDialog] — флаг открытия диалога;
 * - [dialogInstrument] — инструмент, для которого открыт диалог
 *   (composite identity — `tscalpInstrumentId` + `brokerName`);
 * - [selectedBroker] — выбранный брокер в диалоге;
 * - [selectedAccountIdDialog] — выбранный счёт в диалоге;
 * - [dialogAccounts] — счета для диалога;
 * - [swipeResetTrigger] — триггер сброса swipe на карточке после save
 *   (toggled в [OrderDialogsViewModel.closeBrokerDialog] /
 *   [OrderDialogsViewModel.saveBrokerSettings]).
 */
data class OrderDialogsState(
    val showBrokerDialog: Boolean = false,
    val dialogInstrument: Instrument? = null,
    val selectedBroker: BrokerName = BrokerName.TINVEST,
    val selectedAccountIdDialog: String? = null,
    val dialogAccounts: List<BrokerAccount> = emptyList(),
    val swipeResetTrigger: Boolean = false,
)
