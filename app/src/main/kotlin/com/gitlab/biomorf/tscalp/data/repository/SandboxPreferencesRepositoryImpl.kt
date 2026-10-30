package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gitlab.biomorf.tscalp.domain.api.repository.SandboxPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Реализация [SandboxPreferencesRepository] на DataStore (clean-audit Н68, пакет B). */
@Singleton
class SandboxPreferencesRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SandboxPreferencesRepository {
        override suspend fun isSandboxMode(): Boolean = dataStore.data.first()[SettingsKeys.sandboxKey(BrokerName.TINVEST)] ?: true

        override suspend fun setSandboxMode(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[SettingsKeys.sandboxKey(BrokerName.TINVEST)] = enabled
            }
        }
    }
