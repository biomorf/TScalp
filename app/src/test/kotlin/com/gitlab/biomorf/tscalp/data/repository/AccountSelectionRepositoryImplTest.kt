package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSelectionRepositoryImplTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepo(testScope: TestScope): AccountSelectionRepositoryImpl {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher(testScope.testScheduler)),
                produceFile = { tempFolder.newFile("test.preferences_pb") },
            )
        return AccountSelectionRepositoryImpl(dataStore)
    }

    @Test
    fun `selected is null when no value saved`() =
        runTest {
            val repo = createRepo(this)
            assertNull(repo.selected(BrokerName.TINVEST).first())
        }

    @Test
    fun `selected returns saved accountId`() =
        runTest {
            val repo = createRepo(this)
            repo.select(BrokerName.TINVEST, "acc-1")
            assertEquals("acc-1", repo.selected(BrokerName.TINVEST).first())
        }

    @Test
    fun `select overwrites previous`() =
        runTest {
            val repo = createRepo(this)
            repo.select(BrokerName.TINVEST, "acc-1")
            repo.select(BrokerName.TINVEST, "acc-2")
            assertEquals("acc-2", repo.selected(BrokerName.TINVEST).first())
        }

    @Test
    fun `select with null removes value`() =
        runTest {
            val repo = createRepo(this)
            repo.select(BrokerName.TINVEST, "acc-1")
            repo.select(BrokerName.TINVEST, null)
            assertNull(repo.selected(BrokerName.TINVEST).first())
        }

    @Test
    fun `per-broker isolation`() =
        runTest {
            val repo = createRepo(this)
            repo.select(BrokerName.TINVEST, "tinvest-acc")
            repo.select(BrokerName.BCS, "bcs-acc")
            assertEquals("tinvest-acc", repo.selected(BrokerName.TINVEST).first())
            assertEquals("bcs-acc", repo.selected(BrokerName.BCS).first())
        }

    @Test
    fun `clear removes only selected broker`() =
        runTest {
            val repo = createRepo(this)
            repo.select(BrokerName.TINVEST, "tinvest-acc")
            repo.select(BrokerName.BCS, "bcs-acc")
            repo.clear(BrokerName.TINVEST)
            assertNull(repo.selected(BrokerName.TINVEST).first())
            assertEquals("bcs-acc", repo.selected(BrokerName.BCS).first())
        }
}
