package com.gitlab.biomorf.tscalp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Провайдер времени для domain-слоя.
 *
 * В проде — [Clock.System]; в тестах менеджеры конструируются
 * с fake-clock напрямую.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TimeModule {
    @Provides
    @Singleton
    internal fun provideClock(): Clock = Clock.System
}
