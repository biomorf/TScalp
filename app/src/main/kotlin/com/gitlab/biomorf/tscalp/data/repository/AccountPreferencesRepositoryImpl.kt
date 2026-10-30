package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [AccountPreferencesRepository] на DataStore (clean-audit Н68, пакет B). */
@Singleton
class AccountPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : AccountPreferencesRepository {
        override suspend fun saveDefaultAccountId(
            brokerName: BrokerName,
            accountId: String,
        ) {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.defaultAccountKey(brokerName)] = accountId
            }
        }

        override suspend fun loadDefaultAccountId(brokerName: BrokerName): String? = dataStore.data.first()[SettingsKeys.defaultAccountKey(brokerName)]
    }
