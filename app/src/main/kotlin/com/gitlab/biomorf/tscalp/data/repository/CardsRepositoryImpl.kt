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
 * `<brokerName.name>|<ticker>|<accountId>`, каждая карточка на
 * отдельной строке. Пустой `accountId` — пустая строка в третьей
 * позиции. `|` в полях невозможен (`ticker` — alphanumeric,
 * `accountId` — alphanumeric, `brokerName.name` — enum-имя),
 * экранирование не требуется.
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
            private const val PARTS_COUNT_NEW = 4
            private const val PARTS_COUNT_LEGACY = 3
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

        private fun encode(id: CardIdentity): String = "${id.brokerName.name}|${id.tscalpInstrumentId}|${id.ticker}|${id.accountId ?: ""}"

        private fun decode(raw: String): List<CardIdentity> =
            raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split("|")
                when (parts.size) {
                    PARTS_COUNT_NEW -> {
                        val broker =
                            runCatching { BrokerName.valueOf(parts[0]) }.getOrNull()
                                ?: return@mapNotNull null
                        val tscalpInstrumentId = parts[1].ifBlank { return@mapNotNull null }
                        val ticker = parts[2].ifBlank { return@mapNotNull null }
                        val accountId = parts[3].ifBlank { null }
                        CardIdentity(
                            tscalpInstrumentId = tscalpInstrumentId,
                            ticker = ticker,
                            brokerName = broker,
                            accountId = accountId,
                        )
                    }
                    PARTS_COUNT_LEGACY -> {
                        val broker =
                            runCatching { BrokerName.valueOf(parts[0]) }.getOrNull()
                                ?: return@mapNotNull null
                        val ticker = parts[1].ifBlank { return@mapNotNull null }
                        val accountId = parts[2].ifBlank { null }
                        CardIdentity(
                            tscalpInstrumentId = ticker,
                            ticker = ticker,
                            brokerName = broker,
                            accountId = accountId,
                        )
                    }
                    else -> null
                }
            }
    }
