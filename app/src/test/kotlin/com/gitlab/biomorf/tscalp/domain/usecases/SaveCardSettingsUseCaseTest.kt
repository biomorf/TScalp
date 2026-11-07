package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SaveCardSettingsUseCaseTest {
    private val cardsRepository = mockk<CardsRepository>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val useCase = SaveCardSettingsUseCase(cardsRepository, logger)

    @Test
    fun `card exists updates brokerName and accountId`() =
        runBlocking {
            val existing =
                CardIdentity(ticker = "SBER", brokerName = BrokerName.TINVEST, accountId = "t-inv")
            every { cardsRepository.identities } returns flowOf(listOf(existing))

            val r = useCase.execute("SBER", BrokerName.BCS, "bcs-acc")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                cardsRepository.save(
                    match { cards ->
                        cards.size == 1 &&
                            cards[0].ticker == "SBER" &&
                            cards[0].brokerName == BrokerName.BCS &&
                            cards[0].accountId == "bcs-acc"
                    },
                )
            }
        }

    @Test
    fun `card not found is no-op success without save`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())

            val r = useCase.execute("SBER", BrokerName.BCS, "bcs-acc")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 0) { cardsRepository.save(any()) }
        }

    @Test
    fun `exception returns Failure Unknown`() =
        runBlocking {
            every { cardsRepository.identities } returns
                flowOf(listOf(CardIdentity(ticker = "SBER")))
            coEvery { cardsRepository.save(any()) } throws RuntimeException("boom")

            val r = useCase.execute("SBER", BrokerName.BCS, "bcs-acc")

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
        }

    @Test
    fun `cancellation rethrows`() =
        runBlocking {
            every { cardsRepository.identities } returns
                flowOf(listOf(CardIdentity(ticker = "SBER")))
            coEvery { cardsRepository.save(any()) } throws CancellationException()

            try {
                useCase.execute("SBER", BrokerName.BCS, "bcs-acc")
                fail("expected CancellationException")
            } catch (expected: CancellationException) {
                // OK
            }
        }
}
