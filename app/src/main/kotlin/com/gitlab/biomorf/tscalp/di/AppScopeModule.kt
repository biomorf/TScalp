package com.gitlab.biomorf.tscalp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * App-wide CoroutineScope для фоновых корутин, живущих дольше ViewModel.
 *
 * Используется `OrderFormMediator` (C2, PR-4): debounce-корутина
 * `saveFormState` на весь app (одна на процесс). `SupervisorJob` —
 * падение одной корутины не убивает scope; `Dispatchers.IO` — фоновые
 * операции (DataStore).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AppScopeModule {
    @Provides
    @Singleton
    internal fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
