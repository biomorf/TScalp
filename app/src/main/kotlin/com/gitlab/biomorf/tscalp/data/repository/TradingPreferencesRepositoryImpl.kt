package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gitlab.biomorf.tscalp.domain.api.repository.TradingPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [TradingPreferencesRepository] на DataStore (clean-audit Н68, пакет B). */
@Singleton
class TradingPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : TradingPreferencesRepository {
        override suspend fun isConfirmOrdersEnabled(): Boolean = dataStore.data.first()[SettingsKeys.CONFIRM_ORDERS_ENABLED] ?: true

        override suspend fun setConfirmOrdersEnabled(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.CONFIRM_ORDERS_ENABLED] = enabled
            }
        }

        override fun confirmOrdersEnabledFlow(): Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[SettingsKeys.CONFIRM_ORDERS_ENABLED] ?: true
            }
    }
