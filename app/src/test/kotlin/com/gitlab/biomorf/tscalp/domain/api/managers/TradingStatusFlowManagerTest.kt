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
 * менеджере + `runTest` + `mockk coVerify` даёт deadlock (Test worker
 * BLOCKED на мониторе LimitedDispatcher, воркер ждёт mockk-ответ).
 * Интервал [TradingStatusFlowManager.INTERVAL_MS] = 60_000L реального
 * времени не проверяется — это заняло бы 60 с на тест. Проверяется
 * immediate fetch + restart при изменении union + остановка polling.
 */
class TradingStatusFlowManagerTest {
    private val useCase = mockk<UpdateTradingStatusesUseCase>(relaxed = true)

    private fun newManager(): TradingStatusFlowManager =
        TradingStatusFlowManager(
            updateTradingStatusesUseCase = useCase,
            logger = TestLogger,
            managerDispatcher = Dispatchers.Default,
        )

    @Test
    fun `setInterest starts polling and fetches immediately`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.TINVEST, setOf("SBER"))
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
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.TINVEST, setOf("SBER"))
                manager.setInterest(TradingStatusConsumer.ORDERS, BrokerName.TINVEST, setOf("GAZP"))
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
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.TINVEST, setOf("SBER"))
                manager.setInterest(TradingStatusConsumer.ORDERS, BrokerName.TINVEST, setOf("GAZP"))
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
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.TINVEST, setOf("SBER"))
                delay(200)
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
                delay(300)
                coVerify(exactly = 1) { useCase.execute(BrokerName.TINVEST, any()) }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }

    @Test
    fun `two brokers produce two usecase calls`() =
        runBlocking {
            coEvery { useCase.execute(any(), any()) } returns AppResult.Success(emptyMap())
            val manager = newManager()
            try {
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.TINVEST, setOf("SBER"))
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.BCS, setOf("GAZP"))
                delay(200)
                coVerify(exactly = 1) { useCase.execute(BrokerName.TINVEST, listOf("SBER")) }
                coVerify(exactly = 1) { useCase.execute(BrokerName.BCS, listOf("GAZP")) }
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
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.TINVEST, setOf("SBER"))
                delay(200)
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.TINVEST, setOf("SBER"))
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
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.TINVEST, setOf("SBER"))
                delay(200)
                manager.setInterest(TradingStatusConsumer.PORTFOLIO, BrokerName.TINVEST, setOf("GAZP"))
                delay(300)
                coVerify {
                    useCase.execute(BrokerName.TINVEST, match { it.toSet() == setOf("GAZP") })
                }
            } finally {
                manager.clearInterest(TradingStatusConsumer.PORTFOLIO)
            }
        }
}
