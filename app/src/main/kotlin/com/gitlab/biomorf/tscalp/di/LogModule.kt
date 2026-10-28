package com.gitlab.biomorf.tscalp.di

import com.gitlab.biomorf.tscalp.data.log.AndroidAppLogger
import com.gitlab.biomorf.tscalp.domain.log.Logger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LogModule {
    @Binds
    @Singleton
    internal abstract fun bindLogger(impl: AndroidAppLogger): Logger
}
