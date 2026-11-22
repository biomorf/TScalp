package com.gitlab.biomorf.tscalp.domain.api.managers

import com.gitlab.biomorf.tscalp.domain.log.TestLogger
import com.gitlab.biomorf.tscalp.domain.models.AppResult
import com.gitlab.biomorf.tscalp.domain.models.BrokerName
import com.gitlab.biomorf.tscalp.domain.usecases.UpdateTradingStatusesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Тесты [TradingStatusFlowManager] по образцу менеджер-тестов проекта
 * (`PriceFlowManagerTest`, `PositionFlowManagerTest`): `runBlocking` +
 * `Dispatchers.Default` + `TestLogger` + `delay` + `finally`-очистка.
 *
 * **Не** `runTest`/`StandardTestDispatcher`: `limitedParallelism(1)` в
 * менеджере + `runTest` + `coVerify` даёт deadlock (Test worker
 * BLOCKED на мониторе LimitedDispatcher, воркер ждёт mockk-ответ).
 * Fake вместо mockk невозможен: `UpdateTradingStatusesUseCase` — final.
 *
 * Поэтому периодичность проверяется **коротким реальным интервалом**
 * ([INTERVAL_MS_SHORT] = 50 мс) через internal-конструктор, а не
 * `advanceTimeBy`. Продовый [TradingStatusFlowManager.INTERVAL_MS]
 * (60 с) остаётся константой и в тесте не выставляется.
 */
class TradingStatusFlowManagerTest {
    private companion object {
        const val INTERVAL_MS_SHORT = 50L
        const val WAIT_MS = 230L
    }

    private val useCase = mockk<UpdateTradingStatusesUseCase>(relaxed = true)

    private fun newManager(intervalMs: Long? = null): TradingStatusFlowManager =
        if (intervalMs == null) {
            TradingStatusFlowManager(
                updateTradingStatusesUseCase = useCase,
                logger = TestLogger,
                managerDispatcher = Dispatchers.Default,
            )
        } else {
            TradingStatusFlowManager(
                updateTradingStatusesUseCase = useCase,
                logger = TestLogger,
                managerDispatcher = Dispatchers.Default,
                intervalMs = intervalMs,
            )
        }

    @Test
    fun `setInterest starts polling and fetches immediately`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, mapOf(BrokerName.TINVEST to setOf("SBER")))
                delay(200)
                coVerify(exactly = 1) { useCase.execute(BrokerName.TINVEST, listOf("SBER")) }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }

    @Test
    fun `two consumers produce union of ids`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, mapOf(BrokerName.TINVEST to setOf("SBER")))
                manager.setInterest(TradingStatusConsumer.ORDERS, mapOf(BrokerName.TINVEST to setOf("GAZP")))
                delay(200)
                coVerify {
                    useCase.execute(BrokerName.TINVEST, match { it.toSet() == setOf("SBER", "GAZP") })
                }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
                manager.clearInterest(TradingStatusConsumer.ORDERS)
            }
        }

    @Test
    fun `clearInterest of one consumer does not stop polling`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, mapOf(BrokerName.TINVEST to setOf("SBER")))
                manager.setInterest(TradingStatusConsumer.ORDERS, mapOf(BrokerName.TINVEST to setOf("GAZP")))
                delay(200)
                manager.clearInterest(TradingStatusConsumer.ORDERS)
                delay(200)
                coVerify {
                    useCase.execute(BrokerName.TINVEST, match { it.toSet() == setOf("SBER") })
                }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }

    @Test
    fun `clearInterest of the last consumer stops polling`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, mapOf(BrokerName.TINVEST to setOf("SBER")))
                delay(200)
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
                delay(300)
                coVerify(exactly = 1) { useCase.execute(BrokerName.TINVEST, any()) }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }

    @Test
    fun `batch setInterest with two brokers produces one call per broker`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(
                    TradingStatusConsumer.PORTFOLIO,
                    mapOf(
                        BrokerName.TINVEST to setOf("SBER"),
                        BrokerName.BCS to setOf("GAZP"),
                    ),
                )
                delay(200)
                coVerify(exactly = 1) { useCase.execute(BrokerName.TINVEST, listOf("SBER")) }
                coVerify(exactly = 1) { useCase.execute(BrokerName.BCS, listOf("GAZP")) }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }

    @Test
    fun `batch setInterest fires a single immediate round`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(
                    TradingStatusConsumer.PORTFOLIO,
                    mapOf(
                        BrokerName.TINVEST to setOf("SBER"),
                        BrokerName.BCS to setOf("GAZP"),
                    ),
                )
                delay(200)
                coVerify(exactly = 2) { useCase.execute(any(), any()) }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }

    @Test
    fun `same union does not restart polling`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, mapOf(BrokerName.TINVEST to setOf("SBER")))
                delay(200)
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, mapOf(BrokerName.TINVEST to setOf("SBER")))
                delay(200)
                coVerify(exactly = 1) { useCase.execute(BrokerName.TINVEST, any()) }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }

    @Test
    fun `setInterest with changed union triggers refetch`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, mapOf(BrokerName.TINVEST to setOf("SBER")))
                delay(200)
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, mapOf(BrokerName.TINVEST to setOf("GAZP")))
                delay(300)
                coVerify {
                    useCase.execute(BrokerName.TINVEST, match { it.toSet() == setOf("GAZP") })
                }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }

    @Test
    fun `polling repeats at the configured interval`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager(intervalMs = INTERVAL_MS_SHORT)
            try {
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, mapOf(BrokerName.TINVEST to setOf("SBER")))
                delay(WAIT_MS)
                coVerify(atLeast = 3) { useCase.execute(BrokerName.TINVEST, any()) }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }
}
