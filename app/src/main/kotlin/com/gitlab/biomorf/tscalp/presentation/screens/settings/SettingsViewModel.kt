package com.gitlab.biomorf.tscalp.presentation.screens.settings

import androidx.lifecycle.ViewModel
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Root settings ViewModel (clean-audit M006, A2).
 *
 * After panel-VM extraction, this class holds only the broker list
 * for the root `BrokerSettingsContent` TabRow / Pager. All other
 * state and orchestration live in per-panel ViewModels
 * ([TInvestSettingsViewModel], [BcsSettingsViewModel],
 * [FinamSettingsViewModel], [TradeSettingsViewModel],
 * [InfoSettingsViewModel]).
 *
 * `SettingsUiState` removed (step 15): each panel-VM owns its own
 * `*UiState`; the root only needs the broker list, which is not
 * state — `getAvailableBrokerNames()` is a direct call.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val brokerRegistry: BrokerRegistry,
    ) : ViewModel() {
        /** Ключи брокеров в типизированном виде — для TabRow / Pager. */
        fun getAvailableBrokerNames(): List<BrokerName> = brokerRegistry.getAvailableBrokerNames()
    }
