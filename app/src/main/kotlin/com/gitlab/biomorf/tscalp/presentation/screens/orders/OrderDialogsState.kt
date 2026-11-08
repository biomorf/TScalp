package com.gitlab.biomorf.tscalp.presentation.screens.orders

import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.Instrument

/** Слот формы заявки: primary / pair (UI-концепт; domain ролей не знает). */
enum class CardSlot { PRIMARY, PAIR }

/**
 * Состояние диалога выбора брокера и счёта для карточки инструмента.
 *
 * Вынесено из `OrdersUiState` в рамках split `OrdersViewModel`
 * (M013 / A2, шаг 12a). Семантика полей 1:1 с прежним `OrdersUiState`:
 * - [showBrokerDialog] — флаг открытия диалога;
 * - [dialogInstrument] — инструмент, для которого открыт диалог;
 * - [dialogSlot] — слот формы (primary/pair), для роутинга
 *   `onAccountSaved` / `onPairedAccountSaved` в Form-VM;
 * - [selectedBroker] — выбранный брокер в диалоге;
 * - [selectedAccountIdDialog] — выбранный счёт в диалоге
 *   (= `newAccountId` при сохранении);
 * - [dialogAccounts] — счета для диалога;
 * - [swipeResetTrigger] — триггер сброса swipe на карточке после save.
 */
data class OrderDialogsState(
    val showBrokerDialog: Boolean = false,
    val dialogInstrument: Instrument? = null,
    val dialogSlot: CardSlot = CardSlot.PRIMARY,
    val selectedBroker: BrokerName = BrokerName.TINVEST,
    val selectedAccountIdDialog: String? = null,
    val dialogAccounts: List<BrokerAccount> = emptyList(),
    val swipeResetTrigger: Boolean = false,
)
