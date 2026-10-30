package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [BrokerCredentialsRepository] на DataStore (clean-audit Н68, пакет B). */
@Singleton
class BrokerCredentialsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : BrokerCredentialsRepository {
        override suspend fun saveBrokerCredentials(
            brokerName: BrokerName,
            token: String,
            sandbox: Boolean,
        ) {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.tokenKey(brokerName)] = token
                prefs[SettingsKeys.sandboxKey(brokerName)] = sandbox
            }
        }

        override suspend fun loadBrokerCredentials(brokerName: BrokerName): Pair<String, Boolean>? {
            val snapshot = dataStore.data.first()
            val token = snapshot[SettingsKeys.tokenKey(brokerName)] ?: return null
            val sandbox = snapshot[SettingsKeys.sandboxKey(brokerName)] ?: true
            return Pair(token, sandbox)
        }

        override suspend fun clearBrokerCredentials(brokerName: BrokerName) {
            dataStore.edit { prefs ->
                prefs.remove(SettingsKeys.tokenKey(brokerName))
                prefs.remove(SettingsKeys.sandboxKey(brokerName))
            }
        }

        override suspend fun saveToken(
            brokerName: BrokerName,
            token: String,
        ) {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.tokenKey(brokerName)] = token
            }
        }

        override suspend fun hasSavedToken(brokerName: BrokerName): Boolean = dataStore.data.first()[SettingsKeys.tokenKey(brokerName)] != null

        override suspend fun getToken(brokerName: BrokerName): String? = dataStore.data.first()[SettingsKeys.tokenKey(brokerName)]
    }
