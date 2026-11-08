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
 * - [dialogInstrument] — инструмент, для которого открыт диалог;
 * - [dialogOldAccountId] — accountId карточки на момент открытия
 *   (Фаза 1b: передаётся в `SaveCardSettingsUseCase` как `oldAccountId`);
 * - [selectedBroker] — выбранный брокер в диалоге;
 * - [selectedAccountIdDialog] — выбранный счёт в диалоге
 *   (= `newAccountId` при сохранении);
 * - [dialogAccounts] — счета для диалога;
 * - [takenAccountIds] — accountId, уже занятые другими карточками
 *   того же `(uid, broker)` (для disabled в `BrokerAccountDialog`);
 * - [swipeResetTrigger] — триггер сброса swipe на карточке после save.
 */
data class OrderDialogsState(
    val showBrokerDialog: Boolean = false,
    val dialogInstrument: Instrument? = null,
    val dialogOldAccountId: String? = null,
    val selectedBroker: BrokerName = BrokerName.TINVEST,
    val selectedAccountIdDialog: String? = null,
    val dialogAccounts: List<BrokerAccount> = emptyList(),
    val takenAccountIds: Set<String> = emptySet(),
    val swipeResetTrigger: Boolean = false,
)
