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
    fun `ensure adds card when missing`() =
        runBlocking {
            val existing = CardIdentity("uid-sber", BrokerName.TINVEST, "A")
            every { cardsRepository.identities } returns flowOf(listOf(existing))

            val r = useCase.execute(sber, newAccountId = "B")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                cardsRepository.save(
                    match { cards ->
                        cards.size == 2 &&
                            cards.any { it.accountId == "A" } &&
                            cards.any { it.accountId == "B" }
                    },
                )
            }
        }

    @Test
    fun `ensure on empty list creates single card`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())

            val r = useCase.execute(sber, newAccountId = "A")

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
    fun `ensure no-op when card already exists`() =
        runBlocking {
            val existing = CardIdentity("uid-sber", BrokerName.TINVEST, "A")
            every { cardsRepository.identities } returns flowOf(listOf(existing))

            val r = useCase.execute(sber, newAccountId = "A")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 0) { cardsRepository.save(any()) }
        }

    @Test
    fun `ensure adds null-account card when missing`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())

            val r = useCase.execute(sber, newAccountId = null)

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                cardsRepository.save(
                    match { cards ->
                        cards.size == 1 && cards[0].accountId == null
                    },
                )
            }
        }

    @Test
    fun `ensure does not remove stale accounts`() =
        runBlocking {
            // (SBER, A) и (SBER, B) в истории; ensure C — обе остаются.
            val a = CardIdentity("uid-sber", BrokerName.TINVEST, "A")
            val b = CardIdentity("uid-sber", BrokerName.TINVEST, "B")
            every { cardsRepository.identities } returns flowOf(listOf(a, b))

            val r = useCase.execute(sber, newAccountId = "C")

            assertTrue(r is AppResult.Success)
            coVerify(exactly = 1) {
                cardsRepository.save(
                    match { cards ->
                        cards.size == 3 &&
                            cards.any { it.accountId == "A" } &&
                            cards.any { it.accountId == "B" } &&
                            cards.any { it.accountId == "C" }
                    },
                )
            }
        }

    @Test
    fun `exception returns Failure Unknown`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())
            coEvery { cardsRepository.save(any()) } throws RuntimeException("boom")

            val r = useCase.execute(sber, newAccountId = "A")

            assertTrue(r is AppResult.Failure)
            val err = (r as AppResult.Failure).error
            assertTrue(err is AppError.Unknown)
        }

    @Test
    fun `cancellation rethrows`() =
        runBlocking {
            every { cardsRepository.identities } returns flowOf(emptyList())
            coEvery { cardsRepository.save(any()) } throws CancellationException()

            try {
                useCase.execute(sber, newAccountId = "A")
                fail("expected CancellationException")
            } catch (expected: CancellationException) {
                // OK
            }
        }
}
