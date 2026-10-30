package com.gitlab.biomorf.tscalp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Провайдер диспетчера для domain-менеджеров.
 *
 * В проде — [Dispatchers.IO] (Android). Менеджеры добавляют
 * `limitedParallelism(1)` сами (доменное требование сериализации
 * доступа к мутабельному state).
 *
 * На iOS/native — `Dispatchers.Default` (в native `Dispatchers.IO`
 * нет); соответствующий actual появится при KMP-переезде `:app`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DispatcherModule {
    @Provides
    @Singleton
    internal fun provideManagerDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
