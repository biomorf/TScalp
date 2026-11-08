package com.gitlab.biomorf.tscalp.domain.usecases

import com.gitlab.biomorf.tscalp.domain.api.repository.CardsRepository
import com.gitlab.biomorf.tscalp.domain.log.Logger
import com.gitlab.biomorf.tscalp.domain.models.AppError
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.models.CardIdentity
import com.gitlab.biomorf.tscalp.domain.models.Share
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

    private val sber =
        Share(
            brokerName = BrokerName.TINVEST,
            tscalpInstrumentId = "uid-sber",
            ticker = "SBER",
            name = "Сбербанк",
            isin = "RU0009029540",
        )

    @Test
    fun `recreate replaces old account with new`() =
        runBlocking {
            val existing = CardIdentity("uid-sber", BrokerName.TINVEST, "A")
            every { cardsRepository.identities } returns flowOf(listOf(existing))

            val r = useCase.execute(sber, oldAccountId = "A", newAccountId = "B")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                cardsRepository.save(
                    match { cards -> cards.size == 1 && cards[0].accountId == "B" },
                )
            }
        }

    @Test
    fun `create when old is null`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())

            val r = useCase.execute(sber, oldAccountId = null, newAccountId = "A")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                cardsRepository.save(
                    match { cards ->
                        cards.size == 1 &&
                            cards[0].tscalpInstrumentId == "uid-sber" &&
                            cards[0].brokerName == BrokerName.TINVEST &&
                            cards[0].accountId == "A"
                    },
                )
            }
        }

    @Test
    fun `same account is no-op`() =
        runBlocking {
            val existing = CardIdentity("uid-sber", BrokerName.TINVEST, "A")
            every { cardsRepository.identities } returns flowOf(listOf(existing))

            val r = useCase.execute(sber, oldAccountId = "A", newAccountId = "A")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 0) { cardsRepository.save(any()) }
        }

    @Test
    fun `collision returns Failure when new account taken`() =
        runBlocking {
            val taken = CardIdentity("uid-sber", BrokerName.TINVEST, "B")
            every { cardsRepository.identities } returns flowOf(listOf(taken))

            val r = useCase.execute(sber, oldAccountId = "A", newAccountId = "B")

            assertTrue(r is AppResult.Failure)
            coVerify(exactly = 0) { cardsRepository.save(any()) }
        }

    @Test
    fun `different broker card unaffected`() =
        runBlocking {
            val otherBroker = CardIdentity("uid-sber", BrokerName.BCS, "bcs-acc")
            every { cardsRepository.identities } returns flowOf(listOf(otherBroker))

            val r = useCase.execute(sber, oldAccountId = null, newAccountId = "A")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                cardsRepository.save(
                    match { cards ->
                        cards.size == 2 &&
                            cards.any { it.brokerName == BrokerName.BCS && it.accountId == "bcs-acc" } &&
                            cards.any { it.brokerName == BrokerName.TINVEST && it.accountId == "A" }
                    },
                )
            }
        }

    @Test
    fun `exception returns Failure Unknown`() =
        runBlocking {
            every { cardsRepository.identities } returns
                flowOf(listOf(CardIdentity("uid-sber", BrokerName.TINVEST, null)))
            coEvery { cardsRepository.save(any()) } throws RuntimeException("boom")

            val r = useCase.execute(sber, oldAccountId = null, newAccountId = "A")

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
        }

    @Test
    fun `cancellation rethrows`() =
        runBlocking {
            every { cardsRepository.identities } returns
                flowOf(listOf(CardIdentity("uid-sber", BrokerName.TINVEST, null)))
            coEvery { cardsRepository.save(any()) } throws CancellationException()

            try {
                useCase.execute(sber, oldAccountId = null, newAccountId = "A")
                fail("expected CancellationException")
            } catch (expected: CancellationException) {
                // OK
            }
        }
}
