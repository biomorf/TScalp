package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [CardsRepository] на DataStore.
 *
 * Формат хранения — plain, построчно:
 * `<brokerName.name>|<tscalpInstrumentId>|<accountId>`, каждая
 * карточка на отдельной строке. Пустой `accountId` — пустая
 * строка в третьей позиции. `|` в полях невозможен
 * (`tscalpInstrumentId` — alphanumeric, `accountId` —
 * alphanumeric, `brokerName.name` — enum-имя), экранирование
 * не требуется.
 *
 * **Legacy не поддерживается** (2026-11-08): пользователей нет,
 * проект не в проде. Decode — только 3-польный формат.
 *
 * Почему plain, а не `@Serializable`: `CardIdentity` — 3 поля,
 * добавление `kotlinx-serialization-json` как прямой зависимости
 * не оправдано (транзитивно приходит через
 * `ktor-serialization-kotlinx-json`).
 */
@Singleton
class CardsRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : CardsRepository {
        private companion object {
            private const val PARTS_COUNT = 3
        }

        override val identities: Flow<List<CardIdentity>> =
            dataStore.data.map { prefs ->
                decode(prefs[SettingsKeys.ORDER_CARDS] ?: "")
            }

        override suspend fun save(identities: List<CardIdentity>) {
            val raw = identities.joinToString("\n") { encode(it) }
            dataStore.edit { prefs ->
                prefs[SettingsKeys.ORDER_CARDS] = raw
            }
        }

        override suspend fun clear() {
            dataStore.edit { prefs ->
                prefs.remove(SettingsKeys.ORDER_CARDS)
            }
        }

        private fun encode(id: CardIdentity): String = "${id.brokerName.name}|${id.tscalpInstrumentId}|${id.accountId ?: ""}"

        private fun decode(raw: String): List<CardIdentity> =
            raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size != PARTS_COUNT) return@mapNotNull null
                val broker =
                    runCatching { BrokerName.valueOf(parts[0]) }.getOrNull()
                        ?: return@mapNotNull null
                val tscalpInstrumentId = parts[1].ifBlank { return@mapNotNull null }
                val accountId = parts[2].ifBlank { null }
                CardIdentity(
                    tscalpInstrumentId = tscalpInstrumentId,
                    brokerName = broker,
                    accountId = accountId,
                )
            }
    }
