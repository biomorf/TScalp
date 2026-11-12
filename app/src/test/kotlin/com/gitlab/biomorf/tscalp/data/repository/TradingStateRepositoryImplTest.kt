package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TradingStateRepositoryImplTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepo(testScope: TestScope): TradingStateRepositoryImpl {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher(testScope.testScheduler)),
                produceFile = { tempFolder.newFile("test.preferences_pb") },
            )
        return TradingStateRepositoryImpl(dataStore, mockk<Logger>(relaxed = true))
    }

    @Test
    fun `state is null before first load or save`() =
        runTest {
            val repo = createRepo(this)
            assertNull(repo.state.value)
        }

    @Test
    fun `save emits snapshot to state`() =
        runTest {
            val repo = createRepo(this)
            val snapshot =
                TradingStateSnapshot(
                    quantity = 3L,
                    orderType = OrderTypeSelection.Limit,
                )
            repo.save(snapshot)
            assertEquals(snapshot, repo.state.value)
        }

    @Test
    fun `load emits snapshot to state`() =
        runTest {
            val repo = createRepo(this)
            val loaded = repo.load()
            assertEquals(loaded, repo.state.value)
        }

    @Test
    fun `clear sets state to null`() =
        runTest {
            val repo = createRepo(this)
            repo.save(TradingStateSnapshot(quantity = 1L))
            repo.clear()
            assertNull(repo.state.value)
        }

    @Test
    fun `roundtrip preserves all OrderTypeSelection values`() =
        runTest {
            val repo = createRepo(this)
            val values =
                listOf(
                    OrderTypeSelection.Market,
                    OrderTypeSelection.Limit,
                    OrderTypeSelection.StopLoss,
                    OrderTypeSelection.TakeProfit,
                    OrderTypeSelection.StopLimit,
                )
            values.forEach { ot ->
                repo.clear()
                repo.save(TradingStateSnapshot(orderType = ot))
                val loaded = repo.load()
                assertEquals("orderType=$ot", ot, loaded.orderType)
            }
        }

    @Test
    fun `load throws on invalid quantity in DataStore`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    produceFile = { tempFolder.newFile("test-invalid-qty.preferences_pb") },
                )
            val repo =
                TradingStateRepositoryImpl(
                    dataStore,
                    mockk<Logger>(relaxed = true),
                )
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("quantity")] = "abc"
            }
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking { repo.load() }
            }
        }

    @Test
    fun `load throws on invalid paired_multiplier in DataStore`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    produceFile = { tempFolder.newFile("test-invalid-mul.preferences_pb") },
                )
            val repo =
                TradingStateRepositoryImpl(
                    dataStore,
                    mockk<Logger>(relaxed = true),
                )
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("paired_multiplier")] = "1O"
            }
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking { repo.load() }
            }
        }

    @Test
    fun `load throws on unknown order_type key in DataStore`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    produceFile = { tempFolder.newFile("test-invalid-ot.preferences_pb") },
                )
            val repo =
                TradingStateRepositoryImpl(
                    dataStore,
                    mockk<Logger>(relaxed = true),
                )
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("order_type")] = "UnknownKey"
            }
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking { repo.load() }
            }
        }

    @Test
    fun `roundtrip preserves selected and paired CardIdentity`() =
        runTest {
            val repo = createRepo(this)
            val selected =
                CardIdentity(
                    tscalpInstrumentId = "uid-sber",
                    brokerName = BrokerName.TINVEST,
                    accountId = "A",
                )
            val paired =
                CardIdentity(
                    tscalpInstrumentId = "uid-gazp",
                    brokerName = BrokerName.BCS,
                    accountId = null,
                )
            repo.save(TradingStateSnapshot(selectedCard = selected, pairedCard = paired))
            val loaded = repo.load()
            assertEquals(selected, loaded.selectedCard)
            assertEquals(paired, loaded.pairedCard)
        }

    @Test
    fun `load throws on malformed selected_card (not 3 parts)`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    produceFile = { tempFolder.newFile("test-bad-card-parts.preferences_pb") },
                )
            val repo = TradingStateRepositoryImpl(dataStore, mockk<Logger>(relaxed = true))
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("selected_card")] = "TINVEST|uid"
            }
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking { repo.load() }
            }
        }

    @Test
    fun `load throws on unknown broker in paired_card`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    produceFile = { tempFolder.newFile("test-bad-card-broker.preferences_pb") },
                )
            val repo = TradingStateRepositoryImpl(dataStore, mockk<Logger>(relaxed = true))
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("paired_card")] = "UNKNOWN|uid|"
            }
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking { repo.load() }
            }
        }

    @Test
    fun `load throws on blank tscalpInstrumentId in selected_card`() =
        runTest {
            val dataStore =
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    produceFile = { tempFolder.newFile("test-bad-card-uid.preferences_pb") },
                )
            val repo = TradingStateRepositoryImpl(dataStore, mockk<Logger>(relaxed = true))
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("selected_card")] = "TINVEST||A"
            }
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking { repo.load() }
            }
        }
}
