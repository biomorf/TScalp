package com.gitlab.biomorf.tscalp.data.api.common

import android.content.Context
import android.content.res.Resources
import com.gitlab.biomorf.tscalp.domain.log.Logger
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Fail-fast tests for [CustomTrustManager] (clean-audit H79 / H80).
 *
 * Before the fix, `loadCertificateToKeyStore` silently logged and
 * continued when a CA resource was missing or corrupt, leaving TLS
 * setup in a degraded state. Now it throws `IllegalStateException`.
 *
 * Happy path (all 5 CAs loaded -> acceptedIssuers.size >= 5) is
 * covered by [CustomTrustManagerHappyPathTest] (Robolectric +
 * `unitTests.isIncludeAndroidResources = true`, real `res/raw/*.cer`).
 */
class CustomTrustManagerTest {
    private val logger = mockk<Logger>(relaxed = true)

    @Test
    fun `throws IllegalStateException when resource read fails`() {
        val resources = mockk<Resources>()
        val context = mockk<Context>()
        every { context.resources } returns resources
        every { resources.openRawResource(any()) } throws IOException("missing")

        try {
            CustomTrustManager(context, logger)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(
                "message should mention CA load failed: ${e.message}",
                e.message?.contains("CA load failed") == true,
            )
        }
    }

    @Test
    fun `throws IllegalStateException when certificate stream is corrupt`() {
        val resources = mockk<Resources>()
        val context = mockk<Context>()
        every { context.resources } returns resources
        every { resources.openRawResource(any()) } returns
            ByteArrayInputStream("not a certificate".toByteArray())

        try {
            CustomTrustManager(context, logger)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(
                "message should mention CA load failed: ${e.message}",
                e.message?.contains("CA load failed") == true,
            )
        }
    }
}
