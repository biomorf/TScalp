package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountSelectionRepository
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [AccountSelectionRepository] на DataStore
 * (разбиение `OrdersViewModel` на 4 VM, шаг 3).
 *
 * Ключ per-broker: `<brokerName.key>_selected_order_account`.
 * Legacy-формат ключа (по образцу [AccountPreferencesRepositoryImpl]).
 * `select(brokerName, null)` — удаляет значение.
 */
@Singleton
class AccountSelectionRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : AccountSelectionRepository {
        override fun selected(brokerName: BrokerName): Flow<String?> = dataStore.data.map { prefs -> prefs[SettingsKeys.selectedOrderAccountKey(brokerName)] }

        override suspend fun select(
            brokerName: BrokerName,
            accountId: String?,
        ) {
            dataStore.edit { prefs ->
                val key = SettingsKeys.selectedOrderAccountKey(brokerName)
                accountId?.let { prefs[key] = it } ?: prefs.remove(key)
            }
        }

        override suspend fun clear(brokerName: BrokerName) {
            dataStore.edit { prefs ->
                prefs.remove(SettingsKeys.selectedOrderAccountKey(brokerName))
            }
        }
    }
