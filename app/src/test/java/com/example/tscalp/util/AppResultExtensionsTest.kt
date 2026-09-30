package com.example.tscalp.util

import com.example.tscalp.domain.models.AppError
import com.example.tscalp.domain.models.AppResult
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AppResultExtensionsTest {

    @Test
    fun `success path returns Success with value`() {
        val result = runCatchingAppResult { 42 }
        assertTrue(result is AppResult.Success)
        assertEquals(42, (result as AppResult.Success).data)
    }

    @Test
    fun `unknownHostException maps to Network`() {
        val result = runCatchingAppResult { throw UnknownHostException("no internet") }
        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Network)
    }

    @Test
    fun `socketTimeoutException maps to Network`() {
        val result = runCatchingAppResult { throw SocketTimeoutException("timeout") }
        assertTrue((result as AppResult.Failure).error is AppError.Network)
    }

    @Test
    fun `ioexception with UNAUTHENTICATED maps to Auth`() {
        val result = runCatchingAppResult { throw IOException("UNAUTHENTICATED: 40003") }
        assertTrue((result as AppResult.Failure).error is AppError.Auth)
    }

    @Test
    fun `ioexception with NOT_FOUND maps to NotFound`() {
        val result = runCatchingAppResult { throw IOException("NOT_FOUND: 50002") }
        assertTrue((result as AppResult.Failure).error is AppError.NotFound)
    }

    @Test
    fun `ioexception with HTTP code maps to Api with code`() {
        val result = runCatchingAppResult { throw IOException("HTTP 500: server error") }
        val error = (result as AppResult.Failure).error
        assertTrue(error is AppError.Api)
        assertEquals(500, (error as AppError.Api).code)
    }

    @Test
    fun `cancellationException is rethrown not swallowed`() {
        try {
            runCatchingAppResult<Int> { throw CancellationException("cancel") }
            // Should not reach here
            assertTrue("CancellationException should propagate", false)
        } catch (e: CancellationException) {
            // Expected
            assertEquals("cancel", e.message)
        }
    }

    @Test
    fun `generic exception maps to Unknown`() {
        val result = runCatchingAppResult<Int> { throw IllegalStateException("something") }
        assertTrue((result as AppResult.Failure).error is AppError.Unknown)
    }
}