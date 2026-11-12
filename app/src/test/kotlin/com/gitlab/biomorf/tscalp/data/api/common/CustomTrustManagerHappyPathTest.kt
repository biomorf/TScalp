package com.gitlab.biomorf.tscalp.data.api.common

import com.gitlab.biomorf.tscalp.domain.log.Logger
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Happy-path for [CustomTrustManager] (clean-audit H80).
 *
 * Uses Robolectric with `unitTests.isIncludeAndroidResources = true`
 * (set in app/build.gradle.kts), so `res/raw/russian_trusted_*.cer`
 * are accessible via [RuntimeEnvironment.getApplication] context —
 * same `.cer` files the app bundles in production.
 *
 * Verifies:
 * - `CustomTrustManager` initialises without throwing (H79: fail-fast
 *   would throw if any of 5 CAs failed to load).
 * - `acceptedIssuers.size >= 5` — H80 invariant holds.
 *
 * Complements the mockk-based fail-fast tests in
 * [CustomTrustManagerTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@ConscryptMode(ConscryptMode.Mode.OFF)
class CustomTrustManagerHappyPathTest {
    private val logger = mockk<Logger>(relaxed = true)

    @Test
    fun `initializes successfully with all 5 bundled Russian CAs`() {
        val context = RuntimeEnvironment.getApplication()
        val tm = CustomTrustManager(context, logger)
        assertTrue(
            "expected >= 5 accepted issuers, got ${tm.acceptedIssuers.size}",
            tm.acceptedIssuers.size >= 5,
        )
    }
}
