package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import com.gitlab.biomorf.tscalp.domain.models.orderTypeFromStorageKey
import com.gitlab.biomorf.tscalp.domain.models.toStorageKey

/**
 * Репозиторий торгового состояния сессии: выбранные инструменты,
 * количество лотов, множитель, тип заявки.
 *
 * Использует тот же DataStore, что и SettingsRepository, но работает
 * с другим набором ключей. Разделение ответственности: настройки —
 * это долгоживущие пользовательские предпочтения, торговое состояние —
 * это снимок сессии.
 */
@Singleton
class TradingStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    suspend fun save(snapshot: TradingStateSnapshot) {
        dataStore.edit { prefs ->
            snapshot.selectedTscalpInstrumentId?.let {
                prefs[SELECTED_INSTRUMENT_UID] = it
            } ?: prefs.remove(SELECTED_INSTRUMENT_UID)

            snapshot.pairedTscalpInstrumentId?.let {
                prefs[PAIRED_INSTRUMENT_UID] = it
            } ?: prefs.remove(PAIRED_INSTRUMENT_UID)

            prefs[PAIR_TRADING_ENABLED] = snapshot.pairTradingEnabled
            prefs[QUANTITY] = snapshot.quantity
            prefs[PAIRED_MULTIPLIER] = snapshot.pairedMultiplier
            prefs[ORDER_TYPE] = snapshot.orderType.toStorageKey()
        }
    }

    suspend fun load(): TradingStateSnapshot {
        val prefs = dataStore.data.first()
        return TradingStateSnapshot(
            selectedTscalpInstrumentId = prefs[SELECTED_INSTRUMENT_UID],
            pairedTscalpInstrumentId = prefs[PAIRED_INSTRUMENT_UID],
            pairTradingEnabled = prefs[PAIR_TRADING_ENABLED] ?: false,
            quantity = prefs[QUANTITY] ?: "",
            pairedMultiplier = prefs[PAIRED_MULTIPLIER] ?: "10",
            orderType = prefs[ORDER_TYPE]?.let { orderTypeFromStorageKey(it) }
                ?: OrderTypeSelection.Market
        )
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(SELECTED_INSTRUMENT_UID)
            prefs.remove(PAIRED_INSTRUMENT_UID)
            prefs.remove(PAIR_TRADING_ENABLED)
            prefs.remove(QUANTITY)
            prefs.remove(PAIRED_MULTIPLIER)
            prefs.remove(ORDER_TYPE)
        }
    }

    private companion object {
        val SELECTED_INSTRUMENT_UID = stringPreferencesKey("selected_instrument_uid")
        val PAIRED_INSTRUMENT_UID = stringPreferencesKey("paired_instrument_uid")
        val PAIR_TRADING_ENABLED = booleanPreferencesKey("pair_trading_enabled")
        val QUANTITY = stringPreferencesKey("quantity")
        val PAIRED_MULTIPLIER = stringPreferencesKey("paired_multiplier")
        val ORDER_TYPE = stringPreferencesKey("order_type")
    }
}