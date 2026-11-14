package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.TradingStateRepository
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.TradingStateSnapshot
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SaveFormStateUseCaseTest {
    private val tradingStateRepository = mockk<TradingStateRepository>(relaxed = true)
    private val useCase = SaveFormStateUseCase(tradingStateRepository)

    @Test
    fun `execute passes snapshot to repository`() =
        runBlocking {
            val snapshot = TradingStateSnapshot(quantity = 5L, pairedMultiplier = 20L)
            useCase.execute(snapshot)
            coVerify { tradingStateRepository.save(snapshot) }
        }

    @Test
    fun `execute with empty snapshot passes empty`() =
        runBlocking {
            useCase.execute(TradingStateSnapshot())
            coVerify { tradingStateRepository.save(TradingStateSnapshot()) }
        }

    @Test
    fun `execute with both cards passes both`() =
        runBlocking {
            val snapshot =
                TradingStateSnapshot(
                    selectedCard = CardIdentity("uid-1", BrokerName.TINVEST, "acc-1"),
                    pairedCard = CardIdentity("uid-2", BrokerName.BCS, "acc-2"),
                )
            useCase.execute(snapshot)
            coVerify { tradingStateRepository.save(snapshot) }
        }

    @Test
    fun `execute with null cards passes null`() =
        runBlocking {
            val snapshot = TradingStateSnapshot(selectedCard = null, pairedCard = null)
            useCase.execute(snapshot)
            coVerify { tradingStateRepository.save(snapshot) }
        }
}
