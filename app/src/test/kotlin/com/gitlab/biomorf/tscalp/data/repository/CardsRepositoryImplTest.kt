package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CardsRepositoryImplTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepo(testScope: TestScope): CardsRepositoryImpl {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher(testScope.testScheduler)),
                produceFile = { tempFolder.newFile("test.preferences_pb") },
            )
        return CardsRepositoryImpl(dataStore)
    }

    @Test
    fun `identities is empty when no data saved`() =
        runTest {
            val repo = createRepo(this)
            assertTrue(repo.identities.first().isEmpty())
        }

    @Test
    fun `identities returns saved card after save`() =
        runTest {
            val repo = createRepo(this)
            val card = CardIdentity(tscalpInstrumentId = "uid-sber", ticker = "SBER", brokerName = BrokerName.TINVEST, accountId = "acc-1")
            repo.save(listOf(card))
            assertEquals(listOf(card), repo.identities.first())
        }

    @Test
    fun `identities returns empty after clear`() =
        runTest {
            val repo = createRepo(this)
            repo.save(listOf(CardIdentity(tscalpInstrumentId = "uid-sber", ticker = "SBER", brokerName = BrokerName.TINVEST)))
            assertTrue(repo.identities.first().isNotEmpty())
            repo.clear()
            assertTrue(repo.identities.first().isEmpty())
        }

    @Test
    fun `save overwrites previous`() =
        runTest {
            val repo = createRepo(this)
            repo.save(listOf(CardIdentity(tscalpInstrumentId = "uid-sber", ticker = "SBER", brokerName = BrokerName.TINVEST)))
            repo.save(listOf(CardIdentity(tscalpInstrumentId = "uid-gazp", ticker = "GAZP", brokerName = BrokerName.BCS)))
            val result = repo.identities.first()
            assertEquals(1, result.size)
            assertEquals("GAZP", result[0].ticker)
        }
}
