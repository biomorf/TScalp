package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.api.BrokerApi
import com.gitlab.biomorf.tscalp.domain.api.BrokerRegistry
import com.gitlab.biomorf.tscalp.domain.api.repository.AccountPreferencesRepository
import com.gitlab.biomorf.tscalp.domain.api.repository.BrokerCredentialsRepository
import com.gitlab.biomorf.tscalp.domain.api.roles.AccountsApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BcsLifecycleApi
import com.gitlab.biomorf.tscalp.domain.api.roles.BrokerCredentials
import com.gitlab.biomorf.tscalp.domain.api.roles.SandboxApi
import com.gitlab.biomorf.tscalp.domain.api.roles.TInvestLifecycleApi
import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import com.gitlab.biomorf.tscalp.domain.models.BrokerAccount
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BrokerLifecycleManagerTest {
    private val mockRegistry = mockk<BrokerRegistry>(relaxed = true)
    private val mockAccountPreferences =
        mockk<AccountPreferencesRepository>(relaxed = true)
    private val mockBrokerCredentials =
        mockk<BrokerCredentialsRepository>(relaxed = true)
    private val mockPositionFlowManager = mockk<PositionFlowManager>(relaxed = true)
    private val mockBroker = mockk<BrokerApi>(relaxed = true)
    private val mockTInvestLifecycle = mockk<TInvestLifecycleApi>(relaxed = true)
    private val mockBcsLifecycle = mockk<BcsLifecycleApi>(relaxed = true)
    private val mockAccounts = mockk<AccountsApi>(relaxed = true)
    private val mockSandbox = mockk<SandboxApi>(relaxed = true)

    private fun newManager() =
        BrokerLifecycleManager(
            brokerRegistry = mockRegistry,
            accountPreferencesRepository = mockAccountPreferences,
            brokerCredentialsRepository = mockBrokerCredentials,
            positionFlowManager = mockPositionFlowManager,
            logger = TestLogger,
        )

    private fun account(id: String): BrokerAccount {
        val account = mockk<BrokerAccount>()
        every { account.id } returns id
        return account
    }

    private fun setupTInvestBroker() {
        every { mockBroker.lifecycleTInvest } returns mockTInvestLifecycle
        every { mockBroker.accounts } returns mockAccounts
        every { mockBroker.sandbox } returns mockSandbox
    }

    @Test
    fun `restoreTInvest uses saved account id when it is in accounts`() =
        runBlocking {
            setupTInvestBroker()
            coEvery { mockBrokerCredentials.loadBrokerCredentials(BrokerName.TINVEST) } returns
                ("tok" to false)
            coEvery { mockTInvestLifecycle.initialize(any()) } returns true
            coEvery { mockAccountPreferences.loadDefaultAccountId(BrokerName.TINVEST) } returns "acc-1"
            coEvery { mockAccounts.getAccounts(false) } returns listOf(account("acc-1"), account("acc-2"))

            newManager().restoreTInvest(mockBroker)

            coVerify(exactly = 1) { mockTInvestLifecycle.initialize(BrokerCredentials.TInvest("tok", false)) }
            verify(exactly = 1) { mockRegistry.refreshInitializationState() }
            verify(exactly = 1) { mockPositionFlowManager.start("acc-1") }
        }

    @Test
    fun `restoreTInvest falls back to first account when saved is not in list`() =
        runBlocking {
            setupTInvestBroker()
            coEvery { mockBrokerCredentials.loadBrokerCredentials(BrokerName.TINVEST) } returns
                ("tok" to false)
            coEvery { mockTInvestLifecycle.initialize(any()) } returns true
            coEvery { mockAccountPreferences.loadDefaultAccountId(BrokerName.TINVEST) } returns "acc-old"
            coEvery { mockAccounts.getAccounts(false) } returns listOf(account("acc-1"), account("acc-2"))

            newManager().restoreTInvest(mockBroker)

            verify(exactly = 1) { mockPositionFlowManager.start("acc-1") }
            coVerify(exactly = 1) { mockAccountPreferences.saveDefaultAccountId(BrokerName.TINVEST, "acc-1") }
        }

    @Test
    fun `restoreTInvest opens sandbox account when accounts empty and sandbox`() =
        runBlocking {
            setupTInvestBroker()
            coEvery { mockBrokerCredentials.loadBrokerCredentials(BrokerName.TINVEST) } returns
                ("tok" to true)
            coEvery { mockTInvestLifecycle.initialize(any()) } returns true
            coEvery { mockAccountPreferences.loadDefaultAccountId(BrokerName.TINVEST) } returns null
            coEvery { mockAccounts.getAccounts(true) } returns emptyList()
            coEvery { mockSandbox.openSandboxAccount() } returns "sandbox-1"

            newManager().restoreTInvest(mockBroker)

            coVerify(exactly = 1) { mockSandbox.openSandboxAccount() }
            verify(exactly = 1) { mockPositionFlowManager.start("sandbox-1") }
        }

    @Test
    fun `restoreTInvest falls back to saved account when getAccounts throws`() =
        runBlocking {
            setupTInvestBroker()
            coEvery { mockBrokerCredentials.loadBrokerCredentials(BrokerName.TINVEST) } returns
                ("tok" to false)
            coEvery { mockTInvestLifecycle.initialize(any()) } returns true
            coEvery { mockAccountPreferences.loadDefaultAccountId(BrokerName.TINVEST) } returns "acc-saved"
            coEvery { mockAccounts.getAccounts(false) } throws
                RuntimeException("UNAVAILABLE: upstream")

            newManager().restoreTInvest(mockBroker)

            verify(exactly = 1) { mockPositionFlowManager.start("acc-saved") }
        }

    @Test
    fun `restoreTInvest opens sandbox when getAccounts throws and no saved account`() =
        runBlocking {
            setupTInvestBroker()
            coEvery { mockBrokerCredentials.loadBrokerCredentials(BrokerName.TINVEST) } returns
                ("tok" to true)
            coEvery { mockTInvestLifecycle.initialize(any()) } returns true
            coEvery { mockAccountPreferences.loadDefaultAccountId(BrokerName.TINVEST) } returns null
            coEvery { mockAccounts.getAccounts(true) } throws
                RuntimeException("UNAVAILABLE")
            coEvery { mockSandbox.openSandboxAccount() } returns "sandbox-1"

            newManager().restoreTInvest(mockBroker)

            coVerify(exactly = 1) { mockSandbox.openSandboxAccount() }
            verify(exactly = 1) { mockPositionFlowManager.start("sandbox-1") }
        }

    @Test
    fun `restoreBcs initializes and refreshes state`() =
        runBlocking {
            every { mockBroker.lifecycleBcs } returns mockBcsLifecycle
            coEvery { mockBrokerCredentials.loadBrokerCredentials(BrokerName.BCS) } returns
                ("refresh" to true)
            coEvery { mockBcsLifecycle.initialize(any()) } returns true

            newManager().restoreBcs(mockBroker)

            coVerify(exactly = 1) { mockBcsLifecycle.initialize(BrokerCredentials.Bcs("refresh", true)) }
            verify(exactly = 1) { mockRegistry.refreshInitializationState() }
        }
}
