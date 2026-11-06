package com.gitlab.biomorf.tscalp.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BrokerCredentialsRepositoryImplTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepo(testScope: TestScope): BrokerCredentialsRepositoryImpl {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher(testScope.testScheduler)),
                produceFile = { tempFolder.newFile("test.preferences_pb") },
            )
        return BrokerCredentialsRepositoryImpl(dataStore)
    }

    @Test
    fun `hasSavedTokenFlow is false when no token saved`() =
        runTest {
            val repo = createRepo(this)
            assertFalse(repo.hasSavedTokenFlow(BrokerName.TINVEST).first())
        }

    @Test
    fun `hasSavedTokenFlow is true after saveToken`() =
        runTest {
            val repo = createRepo(this)
            repo.saveToken(BrokerName.TINVEST, "token-123")
            assertTrue(repo.hasSavedTokenFlow(BrokerName.TINVEST).first())
        }

    @Test
    fun `hasSavedTokenFlow is false after clearBrokerCredentials`() =
        runTest {
            val repo = createRepo(this)
            repo.saveToken(BrokerName.TINVEST, "token-123")
            assertTrue(repo.hasSavedTokenFlow(BrokerName.TINVEST).first())
            repo.clearBrokerCredentials(BrokerName.TINVEST)
            assertFalse(repo.hasSavedTokenFlow(BrokerName.TINVEST).first())
        }
}
