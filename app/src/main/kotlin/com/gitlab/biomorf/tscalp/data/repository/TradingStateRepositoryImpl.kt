package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий торгового состояния сессии: выбранные инструменты,
 * количество лотов, множитель, тип заявки.
 *
 * Использует тот же DataStore, что и preferences-репозитории, но работает
 * с другим набором ключей. Разделение ответственности: настройки —
 * это долгоживущие пользовательские предпочтения, торговое состояние —
 * это снимок сессии.
 */
@Singleton
class TradingStateRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val logger: Logger,
    ) : TradingStateRepository {
        private val _state = MutableStateFlow<TradingStateSnapshot?>(null)
        override val state: StateFlow<TradingStateSnapshot?> = _state.asStateFlow()

        override suspend fun save(snapshot: TradingStateSnapshot) {
            dataStore.edit { prefs ->
                snapshot.selectedTscalpInstrumentId?.let {
                    prefs[SELECTED_INSTRUMENT_UID] = it
                } ?: prefs.remove(SELECTED_INSTRUMENT_UID)

                snapshot.pairedTscalpInstrumentId?.let {
                    prefs[PAIRED_INSTRUMENT_UID] = it
                } ?: prefs.remove(PAIRED_INSTRUMENT_UID)

                prefs[PAIR_TRADING_ENABLED] = snapshot.pairTradingEnabled
                prefs[QUANTITY] = snapshot.quantity.toString()
                prefs[PAIRED_MULTIPLIER] = snapshot.pairedMultiplier.toString()
                prefs[ORDER_TYPE] = snapshot.orderType.toStorageKey()
            }
            _state.value = snapshot
        }

        override suspend fun load(): TradingStateSnapshot {
            val prefs = dataStore.data.first()
            val snapshot =
                TradingStateSnapshot(
                    selectedTscalpInstrumentId = prefs[SELECTED_INSTRUMENT_UID],
                    pairedTscalpInstrumentId = prefs[PAIRED_INSTRUMENT_UID],
                    pairTradingEnabled = prefs[PAIR_TRADING_ENABLED] ?: false,
                    quantity =
                        prefs[QUANTITY]?.let { raw ->
                            raw.toLongOrNull() ?: invalidValue("quantity", raw)
                        } ?: 1L,
                    pairedMultiplier =
                        prefs[PAIRED_MULTIPLIER]?.let { raw ->
                            raw.toLongOrNull() ?: invalidValue("paired_multiplier", raw)
                        } ?: 10L,
                    orderType =
                        prefs[ORDER_TYPE]?.let { orderTypeFromStorageKey(it) }
                            ?: OrderTypeSelection.Market,
                )
            _state.value = snapshot
            return snapshot
        }

        override suspend fun clear() {
            dataStore.edit { prefs ->
                prefs.remove(SELECTED_INSTRUMENT_UID)
                prefs.remove(PAIRED_INSTRUMENT_UID)
                prefs.remove(PAIR_TRADING_ENABLED)
                prefs.remove(QUANTITY)
                prefs.remove(PAIRED_MULTIPLIER)
                prefs.remove(ORDER_TYPE)
            }
            _state.value = null
        }

        private fun invalidValue(key: String, raw: String): Nothing {
            logger.e(TAG, "invalid $key in DataStore: '$raw'")
            throw IllegalStateException("invalid $key in DataStore: '$raw'")
        }

        private fun OrderTypeSelection.toStorageKey(): String =
            when (this) {
                OrderTypeSelection.Market -> "Market"
                OrderTypeSelection.Limit -> "Limit"
                OrderTypeSelection.StopLoss -> "StopLoss"
                OrderTypeSelection.TakeProfit -> "TakeProfit"
                OrderTypeSelection.StopLimit -> "StopLimit"
            }

        private fun orderTypeFromStorageKey(key: String): OrderTypeSelection =
            when (key) {
                "Market" -> OrderTypeSelection.Market
                "Limit" -> OrderTypeSelection.Limit
                "StopLoss" -> OrderTypeSelection.StopLoss
                "TakeProfit" -> OrderTypeSelection.TakeProfit
                "StopLimit" -> OrderTypeSelection.StopLimit
                else ->
                    invalidValue(
                        "order_type",
                        key,
                    )
            }

        private companion object {
            private const val TAG = "TradingStateRepositoryImpl"
            val SELECTED_INSTRUMENT_UID = stringPreferencesKey("selected_instrument_uid")
            val PAIRED_INSTRUMENT_UID = stringPreferencesKey("paired_instrument_uid")
            val PAIR_TRADING_ENABLED = booleanPreferencesKey("pair_trading_enabled")
            val QUANTITY = stringPreferencesKey("quantity")
            val PAIRED_MULTIPLIER = stringPreferencesKey("paired_multiplier")
            val ORDER_TYPE = stringPreferencesKey("order_type")
        }
    }
