package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.data.api.common.TransportRateLimiter
import com.gitlab.biomorf.tscalp.data.api.tinvest.TInvestRateLimiter
import com.gitlab.biomorf.tscalp.domain.log.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Rate limiter binding (2e -> KMP -> DI).
 *
 * [TInvestRateLimiter] has no Hilt annotations (KMP-safe by construction),
 * wired here via `@Provides`. Same pattern as usecases (`*UseCaseModule.kt`).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object RateLimiterModule {
    @Provides
    @Singleton
    internal fun provideTransportRateLimiter(
        clock: Clock,
        logger: Logger,
    ): TransportRateLimiter = TInvestRateLimiter(clock, logger)
}
