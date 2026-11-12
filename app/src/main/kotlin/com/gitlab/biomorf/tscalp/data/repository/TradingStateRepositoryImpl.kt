package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
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
                snapshot.selectedCard?.let {
                    prefs[SELECTED_CARD] = encodeCard(it)
                } ?: prefs.remove(SELECTED_CARD)

                snapshot.pairedCard?.let {
                    prefs[PAIRED_CARD] = encodeCard(it)
                } ?: prefs.remove(PAIRED_CARD)

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
                    selectedCard = prefs[SELECTED_CARD]?.let { decodeCard("selected_card", it) },
                    pairedCard = prefs[PAIRED_CARD]?.let { decodeCard("paired_card", it) },
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
                prefs.remove(SELECTED_CARD)
                prefs.remove(PAIRED_CARD)
                prefs.remove(PAIR_TRADING_ENABLED)
                prefs.remove(QUANTITY)
                prefs.remove(PAIRED_MULTIPLIER)
                prefs.remove(ORDER_TYPE)
            }
            _state.value = null
        }

        private fun invalidValue(
            key: String,
            raw: String,
        ): Nothing {
            logger.e(TAG, "invalid $key in DataStore: '$raw'")
            throw IllegalStateException("invalid $key in DataStore: '$raw'")
        }

        private fun encodeCard(card: CardIdentity): String =
            "${card.brokerName.name}|${card.tscalpInstrumentId}|${card.accountId ?: ""}"

        private fun decodeCard(
            key: String,
            raw: String,
        ): CardIdentity {
            val parts = raw.split("|")
            if (parts.size != 3) invalidValue(key, raw)
            val broker = BrokerName.valueOf(parts[0])
            if (parts[1].isBlank()) invalidValue(key, raw)
            return CardIdentity(
                tscalpInstrumentId = parts[1],
                brokerName = broker,
                accountId = parts[2].ifBlank { null },
            )
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
            val SELECTED_CARD = stringPreferencesKey("selected_card")
            val PAIRED_CARD = stringPreferencesKey("paired_card")
            val PAIR_TRADING_ENABLED = booleanPreferencesKey("pair_trading_enabled")
            val QUANTITY = stringPreferencesKey("quantity")
            val PAIRED_MULTIPLIER = stringPreferencesKey("paired_multiplier")
            val ORDER_TYPE = stringPreferencesKey("order_type")
        }
    }
