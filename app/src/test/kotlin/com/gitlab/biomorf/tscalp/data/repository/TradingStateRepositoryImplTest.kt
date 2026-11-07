package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.gitlab.biomorf.tscalp.domain.models.OrderTypeSelection
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        return TradingStateRepositoryImpl(dataStore)
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
                    quantity = "3",
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
            repo.save(TradingStateSnapshot(quantity = "1"))
            repo.clear()
            assertNull(repo.state.value)
        }
}
